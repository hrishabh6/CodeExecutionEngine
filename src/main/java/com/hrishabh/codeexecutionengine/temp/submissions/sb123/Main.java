package com.hrishabh.codeexecutionengine.temp.submissions.sb123;

public class Main {
    public static void main(String[] args) {
        Solution sol = new Solution();

        // Test Case 0: Basic Add
        long startTime0 = System.nanoTime();
        try {
            int a0 = 1;
            int b0 = 2;
            int result0 = sol.add(a0, b0);
            long endTime0 = System.nanoTime();
            long duration0 = (endTime0 - startTime0) / 1_000_000;
            String actualOutput0 = String.valueOf(result0);
            String expectedOutput0 = "3";
            System.out.println("TEST_CASE_RESULT: 0,"  + actualOutput0 + "," + duration0 + ",");
        } catch (Exception e) {
            long endTime0 = System.nanoTime();
            long duration0 = (endTime0 - startTime0) / 1_000_000;
            System.out.println("TEST_CASE_RESULT: 0,," + duration0 + "," + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        // Test Case 1: Division by Zero (simulate runtime error)
        long startTime1 = System.nanoTime();
        try {
            int a1 = 10;
            int b1 = 0;
            int result1 = a1 / b1; // This will throw ArithmeticException
            long endTime1 = System.nanoTime();
            long duration1 = (endTime1 - startTime1) / 1_000_000;
            String actualOutput1 = String.valueOf(result1);
            String expectedOutput1 = "Error Expected";
            boolean passed1 = actualOutput1.equals(expectedOutput1);
            System.out.println("TEST_CASE_RESULT: 1," + actualOutput1 + ","  + duration1 + ",");
        } catch (Exception e) {
            long endTime1 = System.nanoTime();
            long duration1 = (endTime1 - startTime1) / 1_000_000;
            System.out.println("TEST_CASE_RESULT: 0,," + duration1 + "," + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
