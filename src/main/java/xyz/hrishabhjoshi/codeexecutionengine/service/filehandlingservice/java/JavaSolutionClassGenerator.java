package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.java;

import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO.QuestionMetadata;

public class JavaSolutionClassGenerator {

    public static String generateSolutionClassContent(CodeSubmissionDTO submissionDto) {
        QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        return "package " + metadata.getFullyQualifiedPackageName() + ";\n\n" + submissionDto.getUserSolutionCode();
    }
}