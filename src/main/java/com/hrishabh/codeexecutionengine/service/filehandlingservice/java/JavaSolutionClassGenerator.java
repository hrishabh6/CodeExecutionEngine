package com.hrishabh.codeexecutionengine.service.filehandlingservice.java;

import com.hrishabh.codeexecutionengine.dto.CodeSubmissionDTO;
import com.hrishabh.codeexecutionengine.dto.CodeSubmissionDTO.QuestionMetadata;

public class JavaSolutionClassGenerator {

    public static String generateSolutionClassContent(CodeSubmissionDTO submissionDto) {
        QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        return "package " + metadata.getFullyQualifiedPackageName() + ";\n\n" + submissionDto.getUserSolutionCode();
    }
}