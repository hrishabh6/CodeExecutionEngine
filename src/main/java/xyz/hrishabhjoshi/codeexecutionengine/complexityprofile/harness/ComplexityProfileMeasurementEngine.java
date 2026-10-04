package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ProfileCaseRequest;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ProfileCaseResult;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.QuestionMetadataDto;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionRuntimeProperties;
import xyz.hrishabhjoshi.codeexecutionengine.service.utils.ManagedProcessRunner;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
public class ComplexityProfileMeasurementEngine {

    private final ManagedProcessRunner processRunner;
    private final ExecutionRuntimeProperties runtimeProperties;
    private final ComplexityProfileExecutionProperties profileProperties;
    private final ObjectMapper objectMapper;

    @Getter
    private volatile int compileInvocationCount;

    public ComplexityProfileMeasurementEngine(
            ManagedProcessRunner processRunner,
            ExecutionRuntimeProperties runtimeProperties,
            ComplexityProfileExecutionProperties profileProperties,
            ObjectMapper objectMapper) {
        this.processRunner = processRunner;
        this.runtimeProperties = runtimeProperties;
        this.profileProperties = profileProperties;
        this.objectMapper = objectMapper;
    }

    public ComplexityProfileJavaHarness.HarnessRunResult execute(ComplexityProfileJobPayload job) {
        compileInvocationCount = 0;
        long profileStart = System.nanoTime();
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("cxe-profile-");
            CompileOutcome compile = compileOnce(job, workspace);
            if (!compile.success()) {
                return ComplexityProfileJavaHarness.HarnessRunResult.compileFailed(compile.message());
            }
            try (URLClassLoader classLoader = URLClassLoader.newInstance(
                    new URL[] {workspace.toUri().toURL()}, getClass().getClassLoader())) {
                Class<?> solutionClass = classLoader.loadClass(compile.fqcn());
                if (ComplexityProfileMutableStaticScanner.hasUnsupportedMutableStaticState(solutionClass)) {
                    return staticStateRejected(job);
                }
                Method target = resolveTargetMethod(solutionClass, job.getQuestionMetadata());
                ComplexityProfileArgumentMaterializer materializer = new ComplexityProfileArgumentMaterializer(objectMapper);
                ComplexityProfileOutputValidator outputValidator = new ComplexityProfileOutputValidator(objectMapper);
                List<ProfileCaseResult> caseResults = new ArrayList<>();
                for (ProfileCaseRequest profileCase : job.getCases()) {
                    if (elapsedMs(profileStart) > profileProperties.getLimits().getMaxTotalProfileMs()) {
                        caseResults.add(failedCase(profileCase, "PROFILE_TIME_BUDGET", "PROFILE_TIME_BUDGET"));
                        continue;
                    }
                    caseResults.add(runCase(
                            target,
                            solutionClass,
                            materializer,
                            outputValidator,
                            job.getQuestionMetadata(),
                            profileCase,
                            profileStart));
                }
                return ComplexityProfileJavaHarness.HarnessRunResult.completed(caseResults);
            }
        } catch (Exception e) {
            log.warn("[PROFILE-HARNESS] job {} failed: {}", job.getExecutionId(), e.getMessage());
            return ComplexityProfileJavaHarness.HarnessRunResult.internalFailed(e.getMessage());
        } finally {
            if (workspace != null) {
                deleteQuietly(workspace);
            }
        }
    }

    private ComplexityProfileJavaHarness.HarnessRunResult staticStateRejected(ComplexityProfileJobPayload job) {
        List<ProfileCaseResult> caseResults = new ArrayList<>();
        for (ProfileCaseRequest profileCase : job.getCases()) {
            caseResults.add(failedCase(profileCase, "UNSUPPORTED_MUTABLE_STATIC_STATE", "UNSUPPORTED_MUTABLE_STATIC_STATE"));
        }
        return ComplexityProfileJavaHarness.HarnessRunResult.completed(caseResults);
    }

    private ProfileCaseResult runCase(
            Method target,
            Class<?> solutionClass,
            ComplexityProfileArgumentMaterializer materializer,
            ComplexityProfileOutputValidator outputValidator,
            QuestionMetadataDto metadata,
            ProfileCaseRequest profileCase,
            long profileStartNs) {
        List<Long> samples = new ArrayList<>();
        int warmups = profileCase.warmups();
        int measured = profileCase.measuredRepeats();
        try {
            for (int i = 0; i < warmups + measured; i++) {
                if (elapsedMs(profileStartNs) > profileProperties.getLimits().getMaxTotalProfileMs()) {
                    return failedCase(profileCase, "PROFILE_TIME_BUDGET", "PROFILE_TIME_BUDGET");
                }
                Object[] args = materializer.materializeArguments(profileCase.input(), metadata.parameters());
                var constructor = solutionClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                Object solution = constructor.newInstance();
                InvocationSample sample = invokeTimed(
                        target,
                        solution,
                        args,
                        profileProperties.getLimits().getPerInvocationTimeoutMs(),
                        profileProperties.getHarness().isCooperativeInProcessTimeouts());
                if (sample.errorCode() != null) {
                    return failedCase(profileCase, sample.errorCode(), sample.errorCode());
                }
                boolean validated = outputValidator.matchesExpected(
                        sample.returnValue(), profileCase.expectedOutput(), metadata);
                String serialized = outputValidator.serializeForLimitCheck(sample.returnValue());
                if (serialized.getBytes(StandardCharsets.UTF_8).length
                        > profileProperties.getLimits().getMaxSerializedOutputBytes()) {
                    return failedCase(profileCase, "OUTPUT_TOO_LARGE", "OUTPUT_TOO_LARGE");
                }
                if (!validated) {
                    return caseResult(profileCase, "OUTPUT_MISMATCH", false, warmups, 0, "OUTPUT_MISMATCH");
                }
                consumeReturnValue(sample.returnValue());
                if (i >= warmups) {
                    samples.add(sample.elapsedNs());
                }
            }
            ComplexityProfileTimingStats.Aggregate aggregate = ComplexityProfileTimingStats.fromSamples(samples);
            return new ProfileCaseResult(
                    profileCase.caseId(),
                    profileCase.caseIdentity(),
                    profileCase.profileCode(),
                    profileCase.profileVersion(),
                    profileCase.profileHash(),
                    profileCase.generatorVersion(),
                    profileCase.variant(),
                    profileCase.sizeVector(),
                    "SUCCESS",
                    true,
                    warmups,
                    aggregate.sampleCount(),
                    aggregate.medianElapsedNs(),
                    aggregate.madElapsedNs(),
                    aggregate.minElapsedNs(),
                    aggregate.maxElapsedNs(),
                    null);
        } catch (ComplexityProfileArgumentMaterializer.UnsupportedMutableInputTypeException e) {
            return failedCase(profileCase, "UNSUPPORTED_MUTABLE_INPUT_TYPE", "UNSUPPORTED_MUTABLE_INPUT_TYPE");
        } catch (IllegalArgumentException e) {
            return failedCase(profileCase, "INPUT_ERROR", "INPUT_ERROR");
        } catch (Exception e) {
            return failedCase(profileCase, "RUNTIME_ERROR", "RUNTIME_ERROR");
        }
    }

    private InvocationSample invokeTimed(
            Method target,
            Object solution,
            Object[] args,
            long timeoutMs,
            boolean cooperativeTimeout) {
        if (!cooperativeTimeout) {
            try {
                return invokeDirect(target, solution, args);
            } catch (ReflectiveOperationException e) {
                return new InvocationSample(null, 0, "RUNTIME_ERROR");
            }
        }
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "cxe-profile-invoke");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<InvocationSample> future = executor.submit(() -> {
                try {
                    return invokeDirect(target, solution, args);
                } catch (ReflectiveOperationException e) {
                    return new InvocationSample(null, 0, "RUNTIME_ERROR");
                }
            });
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return new InvocationSample(null, 0, "TIMEOUT");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.debug("[PROFILE-HARNESS] invocation failed: {}", cause == null ? e.getMessage() : cause.toString());
            return new InvocationSample(null, 0, "RUNTIME_ERROR");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new InvocationSample(null, 0, "RUNTIME_ERROR");
        } finally {
            executor.shutdownNow();
        }
    }

    private static InvocationSample invokeDirect(Method target, Object solution, Object[] args) throws ReflectiveOperationException {
        target.setAccessible(true);
        long start = System.nanoTime();
        Object result = target.invoke(solution, args);
        long elapsed = System.nanoTime() - start;
        return new InvocationSample(result, elapsed, null);
    }

    private CompileOutcome compileOnce(ComplexityProfileJobPayload job, Path workspace) throws IOException, InterruptedException {
        compileInvocationCount++;
        QuestionMetadataDto metadata = job.getQuestionMetadata();
        String pkg = metadata.fullyQualifiedPackageName() == null ? "" : metadata.fullyQualifiedPackageName().trim();
        Path sourceDir = workspace;
        if (!pkg.isEmpty()) {
            sourceDir = workspace.resolve(pkg.replace('.', '/'));
            Files.createDirectories(sourceDir);
        }
        Path sourceFile = sourceDir.resolve("Solution.java");
        Files.writeString(sourceFile, job.getSourceCode(), StandardCharsets.UTF_8);

        List<String> command = new ArrayList<>(runtimeProperties.getRequiredLanguageRuntime("java").compileCommandTokens());
        command.add("-d");
        command.add(workspace.toAbsolutePath().toString());
        command.add(sourceFile.toAbsolutePath().toString());

        ManagedProcessRunner.ProcessExecutionResult result = processRunner.run(
                command,
                workspace,
                s -> { },
                runtimeProperties.getCompilationTimeoutSeconds(),
                "PROFILE_COMPILE",
                true);
        if (result.timedOut() || result.exitCode() != 0) {
            return CompileOutcome.failure(result.output());
        }
        String fqcn = pkg.isEmpty() ? "Solution" : pkg + ".Solution";
        return CompileOutcome.success(fqcn);
    }

    static Method resolveTargetMethod(Class<?> solutionClass, QuestionMetadataDto metadata) throws NoSuchMethodException {
        Class<?>[] paramTypes = metadata.parameters().stream()
                .map(p -> toClass(p.type()))
                .toArray(Class<?>[]::new);
        return solutionClass.getMethod(metadata.functionName(), paramTypes);
    }

    static Class<?> toClass(String type) {
        return switch (type.trim().toLowerCase()) {
            case "int", "integer" -> int.class;
            case "long" -> long.class;
            case "double" -> double.class;
            case "boolean" -> boolean.class;
            case "void" -> void.class;
            case "string" -> String.class;
            case "int[]" -> int[].class;
            case "int[][]" -> int[][].class;
            case "long[]" -> long[].class;
            default -> throw new IllegalArgumentException("unsupported parameter type: " + type);
        };
    }

    private static void consumeReturnValue(Object value) {
        if (value instanceof int[] array) {
            long acc = 0;
            for (int element : array) {
                acc += element;
            }
            if (acc == Long.MIN_VALUE) {
                throw new IllegalStateException("unreachable");
            }
        } else if (value instanceof int[][] matrix) {
            long acc = 0;
            for (int[] row : matrix) {
                for (int element : row) {
                    acc += element;
                }
            }
            if (acc == Long.MIN_VALUE) {
                throw new IllegalStateException("unreachable");
            }
        } else if (value instanceof Number number) {
            if (number.longValue() == Long.MIN_VALUE && number.longValue() == Long.MAX_VALUE) {
                throw new IllegalStateException("unreachable");
            }
        }
    }

    private static ProfileCaseResult failedCase(ProfileCaseRequest profileCase, String outcome, String errorCode) {
        return caseResult(profileCase, outcome, false, profileCase.warmups(), 0, errorCode);
    }

    private static ProfileCaseResult caseResult(
            ProfileCaseRequest profileCase,
            String outcome,
            boolean validated,
            int warmups,
            int sampleCount,
            String errorCode) {
        return new ProfileCaseResult(
                profileCase.caseId(),
                profileCase.caseIdentity(),
                profileCase.profileCode(),
                profileCase.profileVersion(),
                profileCase.profileHash(),
                profileCase.generatorVersion(),
                profileCase.variant(),
                profileCase.sizeVector(),
                outcome,
                validated,
                warmups,
                sampleCount,
                null,
                null,
                null,
                null,
                errorCode);
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    static void deleteQuietly(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    record InvocationSample(Object returnValue, long elapsedNs, String errorCode) {
    }

    record CompileOutcome(boolean success, String fqcn, String message) {
        static CompileOutcome success(String fqcn) {
            return new CompileOutcome(true, fqcn, null);
        }

        static CompileOutcome failure(String message) {
            return new CompileOutcome(false, null, message);
        }
    }
}
