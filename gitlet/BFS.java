package gitlet;

import java.util.LinkedHashSet;
import java.util.PriorityQueue;

/** Breadth first traversal on commits.
 * @author Diraj Thajali
 * */
public class BFS {
    /** Returns the adjacent parents of the given commitID.
     * @param commitID id of a commit
     * @return HashSet of string of adjacent parents of given commitID */
    private static LinkedHashSet<String> parents(String commitID) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Commit curr = Gitlet.getCommit(commitID);
        if (curr.getParent() != null) {
            result.add(curr.getParent());
        }
        if (curr.getSecondParent() != null) {
            result.add(curr.getSecondParent());
        }
        return result;
    }

    /** Returns all the parents of given commitID.
     * @param commitID id of a commit
     * @return HashSet of String of all the parents of given commitID */
    protected static LinkedHashSet<String> bfs(String commitID) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        PriorityQueue<String> fringe = new PriorityQueue<>();
        fringe.add(commitID);
        result.add(commitID);
        while (!fringe.isEmpty()) {
            String curr = fringe.remove();
            for (String parent : parents(curr)) {
                if (!result.contains(parent)) {
                    result.add(parent);
                    fringe.add(parent);
                }
            }
        }
        return result;
    }
}
