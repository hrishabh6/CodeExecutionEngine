// CustomDataStructureGenerator.java

package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.java;



public class CustomDataStructureGenerator {

    public static String generateCustomStructureHelper(String structureName) {
        StringBuilder helper = new StringBuilder();
        switch (structureName) {
            case "ListNode":
                helper.append("    private static ListNode buildListNode(String jsonString) throws com.fasterxml.jackson.core.JsonProcessingException {\n");
                helper.append("        ObjectMapper mapper = new ObjectMapper();\n");
                helper.append("        List<Integer> list = mapper.readValue(jsonString, mapper.getTypeFactory().constructCollectionType(List.class, Integer.class));\n");
                helper.append("        if (list == null || list.isEmpty()) {\n");
                helper.append("            return null;\n");
                helper.append("        }\n");
                helper.append("        ListNode dummy = new ListNode(0);\n");
                helper.append("        ListNode current = dummy;\n");
                helper.append("        for (Integer val : list) {\n");
                helper.append("            current.next = new ListNode(val);\n");
                helper.append("            current = current.next;\n");
                helper.append("        }\n");
                helper.append("        return dummy.next;\n");
                helper.append("    }\n\n");
                break;
            case "TreeNode":
                helper.append("    private static TreeNode buildTreeNode(String jsonString) throws com.fasterxml.jackson.core.JsonProcessingException {\n");
                helper.append("        ObjectMapper mapper = new ObjectMapper();\n");
                helper.append("        List<Integer> list = mapper.readValue(jsonString, mapper.getTypeFactory().constructCollectionType(List.class, Integer.class));\n");
                helper.append("        if (list == null || list.isEmpty() || list.get(0) == null) {\n");
                helper.append("            return null;\n");
                helper.append("        }\n");
                helper.append("        Queue<TreeNode> queue = new LinkedList<>();\n");
                helper.append("        TreeNode root = new TreeNode(list.get(0));\n");
                helper.append("        queue.offer(root);\n");
                helper.append("        int i = 1;\n");
                helper.append("        while (!queue.isEmpty() && i < list.size()) {\n");
                helper.append("            TreeNode current = queue.poll();\n");
                helper.append("            if (i < list.size() && list.get(i) != null) {\n");
                helper.append("                current.left = new TreeNode(list.get(i));\n");
                helper.append("                queue.offer(current.left);\n");
                helper.append("            }\n");
                helper.append("            i++;\n");
                helper.append("            if (i < list.size() && list.get(i) != null) {\n");
                helper.append("                current.right = new TreeNode(list.get(i));\n");
                helper.append("                queue.offer(current.right);\n");
                helper.append("            }\n");
                helper.append("            i++;\n");
                helper.append("        }\n");
                helper.append("        return root;\n");
                helper.append("    }\n\n");
                break;
            case "Node":
                helper.append("    private static Node buildNode(String jsonString) throws com.fasterxml.jackson.core.JsonProcessingException {\n");
                helper.append("        ObjectMapper mapper = new ObjectMapper();\n");
                helper.append("        List<List<Integer>> adjList = mapper.readValue(jsonString, mapper.getTypeFactory().constructCollectionType(List.class, mapper.getTypeFactory().constructCollectionType(List.class, Integer.class)));\n");
                helper.append("        if (adjList == null || adjList.isEmpty()) {\n");
                helper.append("            return null;\n");
                helper.append("        }\n");
                helper.append("        Map<Integer, Node> nodes = new HashMap<>();\n");
                helper.append("        for (int i = 0; i < adjList.size(); i++) {\n");
                helper.append("            nodes.put(i + 1, new Node(i + 1));\n");
                helper.append("        }\n");
                helper.append("        for (int i = 0; i < adjList.size(); i++) {\n");
                helper.append("            Node node = nodes.get(i + 1);\n");
                helper.append("            for (Integer neighborVal : adjList.get(i)) {\n");
                helper.append("                node.neighbors.add(nodes.get(neighborVal));\n");
                helper.append("            }\n");
                helper.append("        }\n");
                helper.append("        return nodes.get(1);\n");
                helper.append("    }\n\n");

                // Add the new output helper method for Node serialization
                helper.append("    private static String convertNodeToAdjacencyList(Node node) throws JsonProcessingException {\n");
                helper.append("        if (node == null) {\n");
                helper.append("            return \"null\";\n");
                helper.append("        }\n");
                helper.append("        Map<Integer, Node> visited = new HashMap<>();\n");
                helper.append("        Queue<Node> queue = new LinkedList<>();\n");
                helper.append("        \n");
                helper.append("        queue.add(node);\n");
                helper.append("        visited.put(node.val, node);\n");
                helper.append("        \n");
                helper.append("        while (!queue.isEmpty()) {\n");
                helper.append("            Node curr = queue.poll();\n");
                helper.append("            for(Node neighbor : curr.neighbors){\n");
                helper.append("                if(!visited.containsKey(neighbor.val)){\n");
                helper.append("                    visited.put(neighbor.val, neighbor);\n");
                helper.append("                    queue.add(neighbor);\n");
                helper.append("                }\n");
                helper.append("            }\n");
                helper.append("        }\n");
                helper.append("        \n");
                helper.append("        List<List<Integer>> adjList = new ArrayList<>();\n");
                helper.append("        int maxVal = visited.keySet().stream().max(Integer::compare).orElse(0);\n");
                helper.append("        for (int i = 0; i < maxVal; i++) {\n");
                helper.append("            adjList.add(new ArrayList<>());\n");
                helper.append("        }\n");
                helper.append("        for(Node n : visited.values()){\n");
                helper.append("            for(Node neighbor : n.neighbors){\n");
                helper.append("                if (adjList.size() > n.val - 1) { // Added a check to prevent index out of bounds\n");
                helper.append("                    adjList.get(n.val - 1).add(neighbor.val);\n");
                helper.append("                }\n");
                helper.append("            }\n");
                helper.append("        }\n");
                helper.append("        ObjectMapper mapper = new ObjectMapper();\n");
                helper.append("        return mapper.writeValueAsString(adjList);\n");
                helper.append("    }\n\n");
                break;
            default:
                break;

        }
        return helper.toString();
    }

    public static String generateCustomStructureClass(String structureName) {
        StringBuilder classDef = new StringBuilder();
        switch (structureName) {
            case "ListNode":
                classDef.append("    private static class ListNode {\n");
                classDef.append("        @JsonProperty\n");
                classDef.append("        int val;\n");
                classDef.append("        @JsonProperty\n");
                classDef.append("        ListNode next;\n");
                classDef.append("        public ListNode() {}\n");
                classDef.append("        public ListNode(int val) { this.val = val; }\n");
                classDef.append("        public ListNode(int val, ListNode next) { this.val = val; this.next = next; }\n");
                classDef.append("    }\n");
                break;
            case "TreeNode":
                classDef.append("    private static class TreeNode {\n");
                classDef.append("        @JsonProperty\n");
                classDef.append("        int val;\n");
                classDef.append("        @JsonProperty\n");
                classDef.append("        TreeNode left;\n");
                classDef.append("        @JsonProperty\n");
                classDef.append("        TreeNode right;\n");
                classDef.append("        public TreeNode() {}\n");
                classDef.append("        public TreeNode(int val) { this.val = val; }\n");
                classDef.append("        public TreeNode(int val, TreeNode left, TreeNode right) {\n");
                classDef.append("            this.val = val;\n");
                classDef.append("            this.left = left;\n");
                classDef.append("            this.right = right;\n");
                classDef.append("        }\n");
                classDef.append("    }\n");
                break;
            case "Node":
                classDef.append("    private static class Node {\n");
                classDef.append("        @JsonProperty\n");
                classDef.append("        public int val;\n");
                classDef.append("        @JsonProperty\n");
                classDef.append("        public List<Node> neighbors;\n");
                classDef.append("        public Node() {\n");
                classDef.append("            neighbors = new ArrayList<Node>();\n");
                classDef.append("        }\n");
                classDef.append("        public Node(int _val) {\n");
                classDef.append("            val = _val;\n");
                classDef.append("            neighbors = new ArrayList<Node>();\n");
                classDef.append("        }\n");
                classDef.append("        public Node(int _val, List<Node> _neighbors) {\n");
                classDef.append("            val = _val;\n");
                classDef.append("            neighbors = _neighbors;\n");
                classDef.append("        }\n");
                classDef.append("    }\n");
                break;
            default:
                break;
        }
        return classDef.toString();
    }





}