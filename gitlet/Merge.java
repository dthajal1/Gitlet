package gitlet;

import java.io.File;
import java.util.Formatter;
import java.util.LinkedHashSet;

/** Merge Series.
* @author Diraj Thajali
 * */
public class Merge {
    /** Finds the latest common parents of given ids.
     * @param currID id of current commit
     * @param givenID id of the given commit
     * @return latest common parents of these ids
     * */
    private static Commit findLatestCommonAncestor(String currID,
                                                   String givenID) {
        LinkedHashSet<String> currParents = BFS.bfs(currID);
        LinkedHashSet<String> givenParents = BFS.bfs(givenID);
        int currCounter = 0;
        int givenCounter = 0;
        String curr = null;
        String given = null;
        for (String id : currParents) {
            if (givenParents.contains(id)) {
                curr = id;
                break;
            }
            currCounter += 1;
        }
        for (String id : givenParents) {
            if (currParents.contains(id)) {
                given = id;
                break;
            }
            givenCounter += 1;
        }
        if (currCounter < givenCounter) {
            if (curr != null) {
                return Gitlet.getCommit(curr);
            }
            System.out.println("Fix this: ancestor is null");
            System.exit(0);

        } else if (givenCounter < currCounter) {
            if (given != null) {
                return Gitlet.getCommit(given);
            }
            System.out.println("Fix this: ancestor is null");
            System.exit(0);
        }
        return Gitlet.getCommit(curr);
    }

    /** Exits with an error message if branchName is the head.
     * @param branchName name of a branch*/
    private static void checkEqualsHead(String branchName) {
        String head = Gitlet.currBranch();
        if (head.equals(branchName)) {
            System.out.println("Cannot merge a branch with itself.");
            System.exit(0);
        }
    }

    /** Exits with an error message if there are blobs in staging are. */
    private static void checkForUncommittedChanges() {
        MyHashMap stageForAdd =
                Utils.readObject(Gitlet.STAGE_FOR_ADD, MyHashMap.class);
        if (!stageForAdd.isEmpty()) {
            System.out.println("You have uncommitted changes.");
            System.exit(0);
        }
    }

    /** Exits with an error message if branchName doesn't exists.
     * @param branchName name of a branch */
    private static void checkBranchExists(String branchName) {
        File givenFile = new File(Gitlet.BRANCHES, branchName);
        if (!givenFile.exists()) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }
    }

    /** Exits with an error message if given id is the same
     * as the id at split point.
     * @param splitPointID id of a commit the split point
     * @param givenID commitId of a commit at given branch in merge */
    private static void isAncestor(String splitPointID,
                                   String givenID) {
        if (splitPointID.equals(givenID)) {
            System.out.println("Given branch is an "
                    + "ancestor of the current branch.");
            System.exit(0);
        }
    }

    /** Exits with an error message if currID is the same
     * as the id at split point.
     * @param splitPointID commit id at the split point
     * @param currID commit id of current commit
     * @param branchName name of the branch */
    private static void isCurrent(String splitPointID,
                                  String currID, String branchName) {
        if (splitPointID.equals(currID)) {
            Checkout.checkoutBranch(branchName);
            System.out.println("Current branch fast-forwarded.");
            System.exit(0);
        }
    }

    /** Merges the current branch with the given branch.
     * @param branchName name of the branch to merge with */
    protected static void merge(String branchName) {
        String head = Gitlet.currBranch();
        File currFile = new File(Gitlet.BRANCHES, head);
        File givenFile = new File(Gitlet.BRANCHES, branchName);
        MyHashMap stageForAdd =
                Utils.readObject(Gitlet.STAGE_FOR_ADD, MyHashMap.class);

        checkBranchExists(branchName);
        checkEqualsHead(branchName);
        checkForUncommittedChanges();

        String currID = Utils.readContentsAsString(currFile);
        String givenID = Utils.readContentsAsString(givenFile);

        Checkout.checkUntrackedFiles(givenID);

        Commit lca = findLatestCommonAncestor(currID, givenID);

        String splitPoint = lca.getCommitID();
        isAncestor(splitPoint, givenID);
        isCurrent(splitPoint, currID, branchName);

        Commit curr = Gitlet.getCommit(currID);
        Commit given = Gitlet.getCommit(givenID);

        mergeGiven(splitPoint, currID, givenID, stageForAdd);

        boolean encounteredConflict = false;

        boolean conflictInMergingFromLca =
                mergeFromLca(splitPoint, currID, givenID, stageForAdd);

        for (String fName : curr.getContents().keySet()) {
            if (!lca.getContents().containsKey(fName)
                    && given.getContents().containsKey(fName)
                    && !given.getContents().get(fName).
                    equals(curr.getContents().get(fName))) {
                encounteredConflict = true;
                handleConflict(curr.getContents().get(fName),
                        given.getContents().get(fName), stageForAdd);
            }
        }
        Utils.writeObject(Gitlet.STAGE_FOR_ADD, stageForAdd);
        Commit mergeCommit =
                new Commit(String.format("Merged %s into %s.",
                        branchName, head), currID, givenID, false);

        if (encounteredConflict || conflictInMergingFromLca) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    /** Merges to currID from givenID by comparing it to lca.
     * @param splitPoint commitID at the split point
     * @param currID commitId of the current commit
     * @param givenID commitID of given branch in merge
     * @param stageForAdd hashMap of files to add to include
     *                    in this merge commit
     * @return {@code true} if merging from lca encountered merge conflict */
    private static boolean mergeFromLca(String splitPoint,
                                        String currID, String givenID,
                                        MyHashMap stageForAdd) {
        Commit given = Gitlet.getCommit(givenID);
        Commit lca = Gitlet.getCommit(splitPoint);
        Commit curr = Gitlet.getCommit(currID);
        boolean conflict = false;
        for (String fName : lca.getContents().keySet()) {
            if (curr.getContents().containsKey(fName)
                    && lca.getContents().get(fName).
                    equals(curr.getContents().get(fName))
                    && !given.getContents().containsKey(fName)) {
                Gitlet.remove(fName);
            }
            if (curr.getContents().containsKey(fName)
                    && lca.getContents().get(fName).
                    equals(curr.getContents().get(fName))
                    && given.getContents().containsKey(fName)
                    && !lca.getContents().get(fName).
                    equals(given.getContents().get(fName))) {
                Checkout.checkoutCommit(givenID, fName);
                stageForAdd.put(fName, lca.getContents().get(fName));
            }
            if (curr.getContents().containsKey(fName)
                    && given.getContents().containsKey(fName)
                    && !lca.getContents().get(fName).
                    equals(curr.getContents().get(fName))
                    && !lca.getContents().get(fName).
                    equals(given.getContents().get(fName))) {
                conflict = true;
                handleConflict(curr.getContents().get(fName),
                        given.getContents().get(fName), stageForAdd);

            }
            if (!curr.getContents().containsKey(fName)
                    && given.getContents().containsKey(fName)
                    && !lca.getContents().get(fName).
                    equals(given.getContents().get(fName))) {
                conflict = true;
                handleConflict("dummy!@#$%",
                        given.getContents().get(fName), stageForAdd);
            }
            if (!given.getContents().containsKey(fName)
                    && curr.getContents().containsKey(fName)
                    && !lca.getContents().get(fName).
                    equals(curr.getContents().get(fName))) {
                conflict = true;
                handleConflict(curr.getContents().get(fName),
                        "dummy!@#$%", stageForAdd);

            }
        }
        return conflict;
    }

    /** Merges to currID from givenID by comparing to splitPoint.
     * @param splitPoint commitID at the split point
     * @param currID commitID of a current commit
     * @param givenID commitID of a commit at the given branch in merge
     * @param stageForAdd hashMap of files to add to include in
     *                    this merge commit */
    private static void mergeGiven(String splitPoint, String currID,
                                   String givenID, MyHashMap stageForAdd) {
        Commit given = Gitlet.getCommit(givenID);
        Commit lca = Gitlet.getCommit(splitPoint);
        Commit curr = Gitlet.getCommit(currID);
        for (String fName: given.getContents().keySet()) {
            if (!lca.getContents().containsKey(fName)
                    && !curr.getContents().containsKey(fName)) {
                Checkout.checkoutCommit(givenID, fName);
                stageForAdd.put(fName, given.getContents().get(fName));
            }
        }
    }

    /** Handles the merge conflict.
     * @param currBlob id of current blob
     * @param givenBlob id of given blob
     * @param stageForAdd hashMap of files to add to include
     *                    in this merge commit */
    private static void handleConflict(String currBlob,
                                       String givenBlob,
                                       MyHashMap stageForAdd) {
        File cFile = Utils.join(Gitlet.OBJECTS, currBlob);
        File gFile = Utils.join(Gitlet.OBJECTS, givenBlob);
        String currentContent = "";
        String givenContent = "";
        if (cFile.exists() && gFile.exists()) {
            currentContent =
                    Utils.readObject(cFile, Blob.class).getContent();
            givenContent =
                    Utils.readObject(gFile, Blob.class).getContent();
        } else if (cFile.exists()) {
            currentContent =
                    Utils.readObject(cFile, Blob.class).getContent();
        } else if (gFile.exists()) {
            givenContent =
                    Utils.readObject(gFile, Blob.class).getContent();
        }
        Blob blob = Gitlet.getBlob(currBlob);
        String currFileName = blob.getName();
        File writeTo = new File(currFileName);

        Formatter result = new Formatter();
        result.format("<<<<<<< HEAD%n");
        if (!currentContent.equals("")) {
            result.format(currentContent.trim() + "%n");
        }
        result.format("=======%n");
        if (!givenContent.equals("")) {
            result.format(givenContent.trim() + "%n");
        }
        result.format(">>>>>>>%n");

        Utils.writeContents(writeTo, result.toString());
        Blob blobToWrite = new Blob(writeTo, false);
        stageForAdd.put(currFileName, blobToWrite.getBlobID());
    }

}
