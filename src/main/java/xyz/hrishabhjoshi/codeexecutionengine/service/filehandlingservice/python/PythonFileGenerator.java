package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.python;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.FileGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Python file generator that creates the necessary Python files for code execution.
 *
 * <p>This component is responsible for generating Python source files from
 * submission data, including:</p>
 * <ul>
 *   <li>main.py - Contains test case execution logic</li>
 *   <li>solution.py - Contains user's solution code and data structures</li>
 * </ul>
 *
 * <p>The generated files are organized in a package directory structure
 * that matches the fully qualified package name from the question metadata.</p>
 *
 * @author Hrishabhj Joshi
 * @version 1.0
 * @since 1.0
 */
@Component("pythonFileGenerator")
public class PythonFileGenerator implements FileGenerator {

    @Autowired
    private PythonMainContentGenerator mainContentGenerator;

    @Autowired
    private PythonSolutionContentGenerator solutionContentGenerator;

    /**
     * Generates all necessary Python files for code execution.
     *
     * <p>This method creates a complete Python package structure with:</p>
     * <ul>
     *   <li>Package directories based on the fully qualified package name</li>
     *   <li>main.py file containing test case execution logic</li>
     *   <li>solution.py file containing the user's solution code</li>
     * </ul>
     *
     * @param submissionDto The submission data containing user code and metadata
     * @param rootPath The root directory where files should be generated
     * @throws IOException if file creation or writing operations fail
     * @throws IllegalArgumentException if required metadata is missing
     */
    @Override
    public void generateFiles(CodeSubmissionDTO submissionDto, Path rootPath) throws IOException {
        CodeSubmissionDTO.QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        if (metadata == null || metadata.getFullyQualifiedPackageName() == null) {
            throw new IllegalArgumentException("QuestionMetadata or fully qualified package name is missing.");
        }

        Path packageDir = createPackageDirectories(rootPath, metadata.getFullyQualifiedPackageName());
        Files.createDirectories(packageDir);

        generateMainFile(submissionDto, packageDir);
        generateSolutionFile(submissionDto, packageDir);
    }

    /**
     * Creates the package directory structure based on the fully qualified package name.
     *
     * <p>Converts dot-separated package names into corresponding directory paths.
     * For example, "com.algocrack.solution.q9" becomes "com/algocrack/solution/q9".</p>
     *
     * @param rootPath The root directory path
     * @param fullyQualifiedPackageName The dot-separated package name
     * @return Path object representing the complete package directory structure
     */
    private Path createPackageDirectories(Path rootPath, String fullyQualifiedPackageName) {
        Path packagePath = rootPath;
        String[] packageParts = fullyQualifiedPackageName.split("\\.");
        for (String part : packageParts) {
            packagePath = packagePath.resolve(part);
        }
        return packagePath;
    }

    /**
     * Generates the main.py file containing test case execution logic.
     *
     * @param submissionDto The submission data
     * @param packageDir The target package directory
     * @throws IOException if file writing fails
     */
    private void generateMainFile(CodeSubmissionDTO submissionDto, Path packageDir) throws IOException {
        String mainFileContent = mainContentGenerator.generateMainContent(submissionDto);
        Path mainFilePath = packageDir.resolve("main.py");
        Files.writeString(mainFilePath, mainFileContent);
    }

    /**
     * Generates the solution.py file containing user's solution code.
     *
     * @param submissionDto The submission data
     * @param packageDir The target package directory
     * @throws IOException if file writing fails
     */
    private void generateSolutionFile(CodeSubmissionDTO submissionDto, Path packageDir) throws IOException {
        String solutionFileContent = solutionContentGenerator.generateSolutionContent(submissionDto);
        Path solutionFilePath = packageDir.resolve("solution.py");
        Files.writeString(solutionFilePath, solutionFileContent);
    }
}
