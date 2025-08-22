package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.java;

public class CustomDataStructureGenerator {

    public static String generateCustomStructureHelper(String structureName) {
        StringBuilder helper = new StringBuilder();
        switch (structureName) {
            case "ListNode":
                // Existing single ListNode builder
                helper.append("    private static ListNode buildListNode(String jsonString, boolean isListType) throws com.fasterxml.jackson.core.JsonProcessingException {\n");
                helper.append("        ObjectMapper mapper = new ObjectMapper();\n");
                helper.append("        if (jsonString.equals(\"null\") || jsonString.equals(\"\")) {\n");
                helper.append("            return null;\n");
                helper.append("        }\n");
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

                // Existing List<ListNode> builder
                helper.append("    private static List<ListNode> buildListNodeList(String jsonString) throws com.fasterxml.jackson.core.JsonProcessingException {\n");
                helper.append("        ObjectMapper mapper = new ObjectMapper();\n");
                helper.append("        if (jsonString.equals(\"[]\")) {\n");
                helper.append("            return new ArrayList<>();\n");
                helper.append("        }\n");
                helper.append("        List<List<Integer>> listOfLists = mapper.readValue(jsonString, mapper.getTypeFactory().constructCollectionType(List.class, mapper.getTypeFactory().constructCollectionType(List.class, Integer.class)));\n");
                helper.append("        List<ListNode> result = new ArrayList<>();\n");
                helper.append("        for (List<Integer> sublist : listOfLists) {\n");
                helper.append("            if (sublist == null || sublist.isEmpty()) {\n");
                helper.append("                result.add(null);\n");
                helper.append("            } else {\n");
                helper.append("                ListNode dummy = new ListNode(0);\n");
                helper.append("                ListNode current = dummy;\n");
                helper.append("                for (Integer val : sublist) {\n");
                helper.append("                    current.next = new ListNode(val);\n");
                helper.append("                    current = current.next;\n");
                helper.append("                }\n");
                helper.append("                result.add(dummy.next);\n");
                helper.append("            }\n");
                helper.append("        }\n");
                helper.append("        return result;\n");
                helper.append("    }\n\n");

                // NEW: ListNode[] array builder
                helper.append("    private static ListNode[] buildListNodeArray(String jsonString) throws com.fasterxml.jackson.core.JsonProcessingException {\n");
                helper.append("        ObjectMapper mapper = new ObjectMapper();\n");
                helper.append("        if (jsonString.equals(\"[]\")) {\n");
                helper.append("            return new ListNode[0];\n");
                helper.append("        }\n");
                helper.append("        List<List<Integer>> listOfLists = mapper.readValue(jsonString, mapper.getTypeFactory().constructCollectionType(List.class, mapper.getTypeFactory().constructCollectionType(List.class, Integer.class)));\n");
                helper.append("        ListNode[] result = new ListNode[listOfLists.size()];\n");
                helper.append("        for (int i = 0; i < listOfLists.size(); i++) {\n");
                helper.append("            List<Integer> sublist = listOfLists.get(i);\n");
                helper.append("            if (sublist == null || sublist.isEmpty()) {\n");
                helper.append("                result[i] = null;\n");
                helper.append("            } else {\n");
                helper.append("                ListNode dummy = new ListNode(0);\n");
                helper.append("                ListNode current = dummy;\n");
                helper.append("                for (Integer val : sublist) {\n");
                helper.append("                    current.next = new ListNode(val);\n");
                helper.append("                    current = current.next;\n");
                helper.append("                }\n");
                helper.append("                result[i] = dummy.next;\n");
                helper.append("            }\n");
                helper.append("        }\n");
                helper.append("        return result;\n");
                helper.append("    }\n\n");
                break;

            case "TreeNode":
                // Add similar array builders for TreeNode
                // ... existing TreeNode builders ...

                // NEW: TreeNode[] array builder
                helper.append("    private static TreeNode[] buildTreeNodeArray(String jsonString) throws com.fasterxml.jackson.core.JsonProcessingException {\n");
                helper.append("        ObjectMapper mapper = new ObjectMapper();\n");
                helper.append("        if (jsonString.equals(\"[]\")) {\n");
                helper.append("            return new TreeNode[0];\n");
                helper.append("        }\n");
                helper.append("        List<List<Integer>> listOfLists = mapper.readValue(jsonString, mapper.getTypeFactory().constructCollectionType(List.class, mapper.getTypeFactory().constructCollectionType(List.class, Integer.class)));\n");
                helper.append("        TreeNode[] result = new TreeNode[listOfLists.size()];\n");
                helper.append("        for (int i = 0; i < listOfLists.size(); i++) {\n");
                helper.append("            List<Integer> treeValues = listOfLists.get(i);\n");
                helper.append("            if (treeValues == null || treeValues.isEmpty() || treeValues.get(0) == null) {\n");
                helper.append("                result[i] = null;\n");
                helper.append("            } else {\n");
                helper.append("                Queue<TreeNode> queue = new LinkedList<>();\n");
                helper.append("                TreeNode root = new TreeNode(treeValues.get(0));\n");
                helper.append("                queue.offer(root);\n");
                helper.append("                int j = 1;\n");
                helper.append("                while (!queue.isEmpty() && j < treeValues.size()) {\n");
                helper.append("                    TreeNode current = queue.poll();\n");
                helper.append("                    if (j < treeValues.size() && treeValues.get(j) != null) {\n");
                helper.append("                        current.left = new TreeNode(treeValues.get(j));\n");
                helper.append("                        queue.offer(current.left);\n");
                helper.append("                    }\n");
                helper.append("                    j++;\n");
                helper.append("                    if (j < treeValues.size() && treeValues.get(j) != null) {\n");
                helper.append("                        current.right = new TreeNode(treeValues.get(j));\n");
                helper.append("                        queue.offer(current.right);\n");
                helper.append("                    }\n");
                helper.append("                    j++;\n");
                helper.append("                }\n");
                helper.append("                result[i] = root;\n");
                helper.append("            }\n");
                helper.append("        }\n");
                helper.append("        return result;\n");
                helper.append("    }\n\n");
                break;

            case "Node":
                // Add similar for Node... (existing code + array builder)
                // ... existing Node builders ...

                // NEW: Node[] array builder
                helper.append("    private static Node[] buildNodeArray(String jsonString) throws com.fasterxml.jackson.core.JsonProcessingException {\n");
                helper.append("        ObjectMapper mapper = new ObjectMapper();\n");
                helper.append("        if (jsonString.equals(\"[]\")) {\n");
                helper.append("            return new Node[0];\n");
                helper.append("        }\n");
                helper.append("        List<List<List<Integer>>> listOfGraphs = mapper.readValue(jsonString, mapper.getTypeFactory().constructCollectionType(List.class, mapper.getTypeFactory().constructCollectionType(List.class, mapper.getTypeFactory().constructCollectionType(List.class, Integer.class))));\n");
                helper.append("        Node[] result = new Node[listOfGraphs.size()];\n");
                helper.append("        for (int i = 0; i < listOfGraphs.size(); i++) {\n");
                helper.append("            List<List<Integer>> adjList = listOfGraphs.get(i);\n");
                helper.append("            if (adjList == null || adjList.isEmpty()) {\n");
                helper.append("                result[i] = null;\n");
                helper.append("            } else {\n");
                helper.append("                Map<Integer, Node> nodes = new HashMap<>();\n");
                helper.append("                for (int j = 0; j < adjList.size(); j++) {\n");
                helper.append("                    nodes.put(j + 1, new Node(j + 1));\n");
                helper.append("                }\n");
                helper.append("                for (int j = 0; j < adjList.size(); j++) {\n");
                helper.append("                    Node node = nodes.get(j + 1);\n");
                helper.append("                    for (Integer neighborVal : adjList.get(j)) {\n");
                helper.append("                        node.neighbors.add(nodes.get(neighborVal));\n");
                helper.append("                    }\n");
                helper.append("                }\n");
                helper.append("                result[i] = nodes.get(1);\n");
                helper.append("            }\n");
                helper.append("        }\n");
                helper.append("        return result;\n");
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

    // Add these methods to the CustomDataStructureGenerator class

    public static String generateListConverterMethods(String structureName) {
        StringBuilder converters = new StringBuilder();

        // ListNode list converter
        switch (structureName) {
            case "ListNode":
                converters.append("    private static String convertListNodeListToJson(List<ListNode> list) throws JsonProcessingException {\n");
                converters.append("        if (list == null) {\n");
                converters.append("            return \"null\";\n");
                converters.append("        }\n");
                converters.append("        List<List<Integer>> result = new ArrayList<>();\n");
                converters.append("        for (ListNode node : list) {\n");
                converters.append("            if (node == null) {\n");
                converters.append("                result.add(new ArrayList<>());\n");
                converters.append("            } else {\n");
                converters.append("                List<Integer> nodeList = new ArrayList<>();\n");
                converters.append("                ListNode current = node;\n");
                converters.append("                while (current != null) {\n");
                converters.append("                    nodeList.add(current.val);\n");
                converters.append("                    current = current.next;\n");
                converters.append("                }\n");
                converters.append("                result.add(nodeList);\n");
                converters.append("            }\n");
                converters.append("        }\n");
                converters.append("        ObjectMapper mapper = new ObjectMapper();\n");
                converters.append("        return mapper.writeValueAsString(result);\n");
                converters.append("    }\n\n");

                // Single ListNode converter
                converters.append("    private static String convertListNodeToJson(ListNode node) throws JsonProcessingException {\n");
                converters.append("        if (node == null) {\n");
                converters.append("            return \"null\";\n");
                converters.append("        }\n");
                converters.append("        List<Integer> result = new ArrayList<>();\n");
                converters.append("        ListNode current = node;\n");
                converters.append("        while (current != null) {\n");
                converters.append("            result.add(current.val);\n");
                converters.append("            current = current.next;\n");
                converters.append("        }\n");
                converters.append("        ObjectMapper mapper = new ObjectMapper();\n");
                converters.append("        return mapper.writeValueAsString(result);\n");
                converters.append("    }\n\n");

                break;
                case "TreeNode":
                    converters.append("    private static String convertTreeNodeListToJson(List<TreeNode> list) throws JsonProcessingException {\n");
                    converters.append("        if (list == null) {\n");
                    converters.append("            return \"null\";\n");
                    converters.append("        }\n");
                    converters.append("        List<List<Integer>> result = new ArrayList<>();\n");
                    converters.append("        for (TreeNode root : list) {\n");
                    converters.append("            if (root == null) {\n");
                    converters.append("                result.add(new ArrayList<>());\n");
                    converters.append("            } else {\n");
                    converters.append("                List<Integer> treeList = new ArrayList<>();\n");
                    converters.append("                Queue<TreeNode> queue = new LinkedList<>();\n");
                    converters.append("                queue.offer(root);\n");
                    converters.append("                while (!queue.isEmpty()) {\n");
                    converters.append("                    TreeNode current = queue.poll();\n");
                    converters.append("                    if (current != null) {\n");
                    converters.append("                        treeList.add(current.val);\n");
                    converters.append("                        queue.offer(current.left);\n");
                    converters.append("                        queue.offer(current.right);\n");
                    converters.append("                    } else {\n");
                    converters.append("                        treeList.add(null);\n");
                    converters.append("                    }\n");
                    converters.append("                }\n");
                    converters.append("                // Remove trailing nulls\n");
                    converters.append("                while (!treeList.isEmpty() && treeList.get(treeList.size() - 1) == null) {\n");
                    converters.append("                    treeList.remove(treeList.size() - 1);\n");
                    converters.append("                }\n");
                    converters.append("                result.add(treeList);\n");
                    converters.append("            }\n");
                    converters.append("        }\n");
                    converters.append("        ObjectMapper mapper = new ObjectMapper();\n");
                    converters.append("        return mapper.writeValueAsString(result);\n");
                    converters.append("    }\n\n");

                    // Single TreeNode converter
                    converters.append("    private static String convertTreeNodeToJson(TreeNode root) throws JsonProcessingException {\n");
                    converters.append("        if (root == null) {\n");
                    converters.append("            return \"null\";\n");
                    converters.append("        }\n");
                    converters.append("        List<Integer> result = new ArrayList<>();\n");
                    converters.append("        Queue<TreeNode> queue = new LinkedList<>();\n");
                    converters.append("        queue.offer(root);\n");
                    converters.append("        while (!queue.isEmpty()) {\n");
                    converters.append("            TreeNode current = queue.poll();\n");
                    converters.append("            if (current != null) {\n");
                    converters.append("                result.add(current.val);\n");
                    converters.append("                queue.offer(current.left);\n");
                    converters.append("                queue.offer(current.right);\n");
                    converters.append("            } else {\n");
                    converters.append("                result.add(null);\n");
                    converters.append("            }\n");
                    converters.append("        }\n");
                    converters.append("        // Remove trailing nulls\n");
                    converters.append("        while (!result.isEmpty() && result.get(result.size() - 1) == null) {\n");
                    converters.append("            result.remove(result.size() - 1);\n");
                    converters.append("        }\n");
                    converters.append("        ObjectMapper mapper = new ObjectMapper();\n");
                    converters.append("        return mapper.writeValueAsString(result);\n");
                    converters.append("    }\n\n");
                    break;
                    case "Node":

                        // Node list converter
                        converters.append("    private static String convertNodeListToJson(List<Node> list) throws JsonProcessingException {\n");
                        converters.append("        if (list == null) {\n");
                        converters.append("            return \"null\";\n");
                        converters.append("        }\n");
                        converters.append("        List<List<List<Integer>>> result = new ArrayList<>();\n");
                        converters.append("        for (Node node : list) {\n");
                        converters.append("            if (node == null) {\n");
                        converters.append("                result.add(new ArrayList<>());\n");
                        converters.append("            } else {\n");
                        converters.append("                String adjListJson = convertNodeToAdjacencyList(node);\n");
                        converters.append("                ObjectMapper mapper = new ObjectMapper();\n");
                        converters.append("                List<List<Integer>> adjList = mapper.readValue(adjListJson, mapper.getTypeFactory().constructCollectionType(List.class, mapper.getTypeFactory().constructCollectionType(List.class, Integer.class)));\n");
                        converters.append("                result.add(adjList);\n");
                        converters.append("            }\n");
                        converters.append("        }\n");
                        converters.append("        ObjectMapper mapper = new ObjectMapper();\n");
                        converters.append("        return mapper.writeValueAsString(result);\n");
                        converters.append("    }\n\n");


        }

        return converters.toString();
    }



}