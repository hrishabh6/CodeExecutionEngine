package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.python;

import org.springframework.stereotype.Component;

@Component
public class PythonCustomDSGenerator {

    public static String generateHelperMethod(String structureName) {
        StringBuilder helper = new StringBuilder();
        switch (structureName) {
            case "ListNode":
                helper.append("def build_list_node(values, is_list_type=None):\n")
                        .append("    if not values:\n")
                        .append("        # If is_list_type is specified, use it; otherwise infer from structure\n")
                        .append("        if is_list_type is True:\n")
                        .append("            return []\n")
                        .append("        elif is_list_type is False:\n")
                        .append("            return None\n")
                        .append("        else:\n")
                        .append("            # Fallback: assume List[ListNode] for backward compatibility\n")
                        .append("            return []\n")
                        .append("    \n")
                        .append("    # Check if this is a list of lists (for List[ListNode] parameters)\n")
                        .append("    if isinstance(values[0], list):\n")
                        .append("        result = []\n")
                        .append("        for sublist in values:\n")
                        .append("            if not sublist:\n")
                        .append("                result.append(None)\n")
                        .append("            else:\n")
                        .append("                dummy = ListNode(0)\n")
                        .append("                current = dummy\n")
                        .append("                for val in sublist:\n")
                        .append("                    current.next = ListNode(val)\n")
                        .append("                    current = current.next\n")
                        .append("                result.append(dummy.next)\n")
                        .append("        return result\n")
                        .append("    \n")
                        .append("    # Handle single list (for ListNode parameters)\n")
                        .append("    else:\n")
                        .append("        dummy = ListNode(0)\n")
                        .append("        current = dummy\n")
                        .append("        for val in values:\n")
                        .append("            current.next = ListNode(val)\n")
                        .append("            current = current.next\n")
                        .append("        return dummy.next\n\n");
                break;

            case "TreeNode":
                helper.append("def build_tree_node(values, is_list_type=None):\n")
                        .append("    if not values:\n")
                        .append("        # If is_list_type is specified, use it; otherwise infer from structure\n")
                        .append("        if is_list_type is True:\n")
                        .append("            return []\n")
                        .append("        elif is_list_type is False:\n")
                        .append("            return None\n")
                        .append("        else:\n")
                        .append("            # Fallback: assume single TreeNode\n")
                        .append("            return None\n")
                        .append("    \n")
                        .append("    # Check if this is a list of lists (for List[TreeNode] parameters)\n")
                        .append("    if isinstance(values[0], list):\n")
                        .append("        result = []\n")
                        .append("        for tree_values in values:\n")
                        .append("            if not tree_values or tree_values[0] is None:\n")
                        .append("                result.append(None)\n")
                        .append("            else:\n")
                        .append("                from collections import deque\n")
                        .append("                root = TreeNode(tree_values[0])\n")
                        .append("                queue = deque([root])\n")
                        .append("                i = 1\n")
                        .append("                while queue and i < len(tree_values):\n")
                        .append("                    current = queue.popleft()\n")
                        .append("                    if i < len(tree_values) and tree_values[i] is not None:\n")
                        .append("                        current.left = TreeNode(tree_values[i])\n")
                        .append("                        queue.append(current.left)\n")
                        .append("                    i += 1\n")
                        .append("                    if i < len(tree_values) and tree_values[i] is not None:\n")
                        .append("                        current.right = TreeNode(tree_values[i])\n")
                        .append("                        queue.append(current.right)\n")
                        .append("                    i += 1\n")
                        .append("                result.append(root)\n")
                        .append("        return result\n")
                        .append("    \n")
                        .append("    # Handle single tree (for TreeNode parameters)\n")
                        .append("    else:\n")
                        .append("        if values[0] is None:\n")
                        .append("            return None\n")
                        .append("        from collections import deque\n")
                        .append("        root = TreeNode(values[0])\n")
                        .append("        queue = deque([root])\n")
                        .append("        i = 1\n")
                        .append("        while queue and i < len(values):\n")
                        .append("            current = queue.popleft()\n")
                        .append("            if i < len(values) and values[i] is not None:\n")
                        .append("                current.left = TreeNode(values[i])\n")
                        .append("                queue.append(current.left)\n")
                        .append("            i += 1\n")
                        .append("            if i < len(values) and values[i] is not None:\n")
                        .append("                current.right = TreeNode(values[i])\n")
                        .append("                queue.append(current.right)\n")
                        .append("            i += 1\n")
                        .append("        return root\n\n");
                break;

            case "Node":
                helper.append("def build_node(adj_data, is_list_type=None):\n")
                        .append("    if not adj_data:\n")
                        .append("        # If is_list_type is specified, use it; otherwise infer from structure\n")
                        .append("        if is_list_type is True:\n")
                        .append("            return []\n")
                        .append("        elif is_list_type is False:\n")
                        .append("            return None\n")
                        .append("        else:\n")
                        .append("            # Fallback: assume single Node\n")
                        .append("            return None\n")
                        .append("    \n")
                        .append("    # Check if this is a list of adjacency lists (for List[Node] parameters)\n")
                        .append("    if isinstance(adj_data[0], list) and len(adj_data[0]) > 0 and isinstance(adj_data[0][0], list):\n")
                        .append("        result = []\n")
                        .append("        for adj_list in adj_data:\n")
                        .append("            if not adj_list:\n")
                        .append("                result.append(None)\n")
                        .append("            else:\n")
                        .append("                nodes = {i + 1: Node(i + 1) for i in range(len(adj_list))}\n")
                        .append("                for i, neighbors in enumerate(adj_list):\n")
                        .append("                    node = nodes[i + 1]\n")
                        .append("                    node.neighbors = [nodes[n] for n in neighbors if n in nodes]\n")
                        .append("                result.append(nodes.get(1))\n")
                        .append("        return result\n")
                        .append("    \n")
                        .append("    # Handle single graph (for Node parameters)\n")
                        .append("    else:\n")
                        .append("        nodes = {i + 1: Node(i + 1) for i in range(len(adj_data))}\n")
                        .append("        for i, neighbors in enumerate(adj_data):\n")
                        .append("            node = nodes[i + 1]\n")
                        .append("            node.neighbors = [nodes[n] for n in neighbors if n in nodes]\n")
                        .append("        return nodes.get(1)\n\n");
                break;
            default:
                return "";
        }
        return helper.toString();
    }

    public static String generateClass(String structureName) {
        StringBuilder classDef = new StringBuilder();
        switch (structureName) {
            case "ListNode":
                classDef.append("class ListNode:\n    def __init__(self, val=0, next=None):\n        self.val = val\n        self.next = next\n\n");
                break;
            case "TreeNode":
                classDef.append("class TreeNode:\n    def __init__(self, val=0, left=None, right=None):\n        self.val = val\n        self.left = left\n        self.right = right\n\n");
                break;
            case "Node":
                classDef.append("class Node:\n    def __init__(self, val=0, neighbors=None):\n        self.val = val\n        self.neighbors = neighbors if neighbors is not None else []\n\n");
                break;
            default:
                return "";
        }
        return classDef.toString();
    }
}