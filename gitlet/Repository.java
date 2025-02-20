package gitlet;

import java.io.File;
import java.util.*;

import static gitlet.Utils.*;
import static gitlet.Commit.Cmt;



/** Represents a gitlet repository.
 *  This file will handle all the actual git commands.
 *
 *  @author Daniel Feng
 */
public class Repository {

    /** The current working directory. */
    public static final File CWD =  new File(System.getProperty("user.dir"));
    // join("/Users/hf/JavaProjects/CS61B/CS61B/proj2/myTest");

    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");

    /** The directory that saves the Serialised Fields of this class. */
    public static final File OBJ_DIR = join(GITLET_DIR, "obj");

    /** The Instance of Class Blobs which manages the blobs. */
    private Blobs blobs;

    /** The Instance of Class Branches which manages the branches. */
    private Branches branches;

    /** The instance of Class Commit which manages the commits. */
    private Commit commits;

    /** The instance of Class Stage which manages the stage. */
    private Stage stage;

    /**
     * Return a Repository instance if the objects were saved in the disk.
     */
    public static Repository fromFile() {
        if (!GITLET_DIR.exists()) {
            exitWithMsg("Not in an initialized Gitlet directory.");
        }
        return initField();
    }

    /**
     * Creates a new Gitlet version-control system in the current directory.
     * This method will automatically generate an init commit and a branch called master.
     * This method will save the commit and branch to the disk.
     * Call Method fromFile() to get the Gitlet Instance.
     */
    public static void init() {
        if (GITLET_DIR.exists()) { // if Gitlet dir exist, do NOT overwrite it.
            exitWithMsg("A Gitlet version-control system already exists "
                    + "in the current directory.");
        }

        // Create the folders.
        if (!GITLET_DIR.mkdir() || !OBJ_DIR.mkdir()) {
            throw error("Error when create folders");
        }

        initField();
    }

    /** Read the Fields from disk or generate new ones. */
    private static Repository initField() {
        Repository repo = new Repository();
        try {
            repo.blobs = new Blobs();
            repo.commits = new Commit();
            repo.branches = new Branches();
            repo.stage = new Stage();
            return repo;
        } catch (IllegalArgumentException  e) {
            throw error(e.getMessage());
        }
    }

    /** Add a file which exists in the CWD to the stage. */
    public void add(String fileName) {
        File file = join(CWD, fileName);
        if (!file.exists() || !file.isFile()) {
            exitWithMsg("File does not exist.");
        }

        if (stage.removedStageHas(fileName)) {
            stage.removeFromRemovedStage(fileName);
        }

        byte[] fileContent = readContents(file);
        String hashCode = sha1(fileContent);

        Cmt head = commits.getCommit(branches.getHead());
        if (Commit.commitHasFile(head, fileName)
                && Commit.getHashOfFile(head, fileName).equals(hashCode)) {
            return;
        }

        blobs.addBlob(hashCode, fileContent);
        blobs.saveBlobs();
        stage.setStage(fileName, hashCode);
        stage.saveStage();
    }

    /** Commit to the repository.
     *  1. Call Add to the files which were tracked in last commit and stage,
     *     to follow the version change, also prevent the files in the removedStage.
     *  2. Use the treeMap of stage to commit.
     *  3. Then init the stage.
     *
     *  @param message the message of commit, cannot be null.
     */
    public void commit(String message) {
        if (message.length() == 0) { // error when has NOT a commit message
            exitWithMsg("Please enter a commit message.");
        }

        TreeMap<String, String> tree = buildCommitTree();

        if (tree.isEmpty()) { // quit if NOT changed
            exitWithMsg("No changes added to the commit.");
        }

        commit(message, tree, branches.getHead());

    }

    /** A helper method for Commit to commit. */
    private void commit(String message, TreeMap<String, String> tree, String parent) {
        String newHashCode = commits.newCommit(message, tree, parent);
        stage.initStage();
        branches.setCurrentHead(newHashCode);
    }

    /** Return a commit tree. */
    private TreeMap<String, String> buildCommitTree() {
        TreeMap<String, String> tree = new TreeMap<>();

        Cmt currCommit = commits.getCommit(branches.getHead());
        String[] commitFiles = Commit.getFileNames(currCommit);
        if (commitFiles != null) {
            for (String fileName: commitFiles) { // deal with the currCommit
                tree.put(fileName, Commit.getHashOfFile(currCommit, fileName));
            }
        }

        String[] stageFiles = stage.getFilesFromStage();
        if (stageFiles != null) {
            for (String fileName: stageFiles) { // deal with the stage
                tree.put(fileName, stage.getHashForFileInStage(fileName));
            }
        }

        String[] removedFiles = stage.getFilesFromRemovedStage();
        if (removedFiles != null) {
            for (String fileName : removedFiles) {
                tree.remove(fileName);
            }
        }

        return tree;
    }

    /** Un-track a file.
     *  1. If exist in last commit, add to removedStage and delete the file.
     *  2. If exist in the stage, un-stage it.
     */
    public void rm(String fileName) {

        Cmt currCommit = commits.getCommit(branches.getHead());
        boolean inLastCommit = Commit.commitHasFile(currCommit, fileName);
        boolean inStage = stage.stageHas(fileName);

        if (!inLastCommit && !inStage) {
            exitWithMsg("No reason to remove the file.");
        }

        if (inLastCommit) {
            stage.setRemovedStage(fileName);
            restrictedDelete(join(CWD, fileName));
        }

        if (inStage) {
            String hashCode = stage.removeFromStage(fileName);
            blobs.removeBlob(hashCode);
        }
    }

    /** Print all logs of the current commit and all parents. */
    public void log() {
        log(branches.getHead());
    }

    /** Help method for recursive call. */
    private void log(String hashCode) {
        Cmt commit = commits.getCommit(hashCode);
        printHelper(commit);

        String parent = Commit.getParent(commit);
        if (parent == null) {
            return;
        }
        log(parent);
    }

    /** Print all logs of all commits. */
    public void globalLog() {
        for (Cmt commit : commits.getAllCommits()) {
            printHelper(commit);
        }
    }

    /** Print the Fields of a commit. */
    private void printHelper(Cmt commit) {
        System.out.println("===");
        System.out.println("commit " + Commit.getHash(commit));
        System.out.println("Date: " + Commit.getDateTime(commit));
        System.out.println(Commit.getMessage(commit));
        System.out.println(" ");
    }

    /** Print the Hash Value of a commit by the message. */
    public void find(String message) {
        Cmt[] cmts = commits.getCommit(true, message);

        if (cmts == null) {
            exitWithMsg("Found no commit with that message.");
        }

        for (Cmt commit : cmts) {
            System.out.println(Commit.getHash(commit));
        }
    }

    /** Print Fields: Branches, Stage, Removed Stage.
     * As well as modified but not staged and untracked files.
     */
    public void status() {

        String[] branchNames = branches.getBranches();
        String currBranch = branches.getCurrBranch();
        for (int i = 0; i < branchNames.length; i++) {
            if (branchNames[i].equals(currBranch)) {
                branchNames[i] = "*" + branchNames[i];
            }
        }
        statusPrintHelper("Branches", branchNames);

        String[] stageFiles = stage.getFilesFromStage();
        statusPrintHelper("Staged Files", stageFiles);

        String[] removedStageFiles = stage.getFilesFromRemovedStage();
        statusPrintHelper("Removed Files", removedStageFiles);

        String[] modifiedFiles = getModifiedFiles();
        statusPrintHelper("Modifications Not Staged For Commit", modifiedFiles);

        String[] unTrackedFiles = getUnTrackedFiles();
        statusPrintHelper("Untracked Files", unTrackedFiles);

    }

    /** A helper method for Method Status to find out the un-tracked files. */
    private String[] getUnTrackedFiles() {
        ArrayList<String> res = new ArrayList<>();

        List<String> cwdFiles = plainFilenamesIn(CWD);

        if (cwdFiles == null) {
            return null;
        }

        Cmt currCommit = commits.getCommit(branches.getHead());

        for (String fileName : cwdFiles) {
            if ((!Commit.commitHasFile(currCommit, fileName)
                    && !stage.stageHas(fileName))
                    || stage.removedStageHas(fileName)) {
                res.add(fileName);
            }
        }

        if (res.isEmpty()) {
            return null;
        }
        return res.toArray(new String[0]);
    }

    /** A helper method for Method Status to find out the modified files. */
    private String[] getModifiedFiles() {
        ArrayList<String> res = new ArrayList<>();

        Cmt currCommit = commits.getCommit(branches.getHead());
        String[] currFiles = Commit.getFileNames(currCommit);
        if (currFiles != null) {
            for (String fileName : currFiles) {
                File cwdFile = join(CWD, fileName);
                // Not staged for removal, but tracked in the current commit
                // and deleted from the working directory.
                if (!cwdFile.exists() && !stage.removedStageHas(fileName)) {
                    res.add(fileName + " (deleted)");
                    // Tracked in the current commit,
                    // changed in the working directory, but not staged;
                } else if (cwdFile.exists()
                        && !stage.stageHas(fileName)
                        && !Commit.getHashOfFile(currCommit, fileName).equals(
                        sha1(readContents(cwdFile)))) {
                    res.add(fileName + " (modified)");
                }
            }
        }

        String[] stageFiles = stage.getFilesFromStage();
        if (stageFiles != null) {
            for (String fileName : stageFiles) {
                File cwdFile = join(CWD, fileName);
                // Staged for addition, but deleted in the working directory;
                if (!cwdFile.exists()) {
                    res.add(fileName + " (deleted)");
                    // Staged for addition,
                    // but with different contents than in the working directory;
                } else if (!stage.getHashForFileInStage(fileName).equals(
                        sha1(readContents(cwdFile)))) {
                    res.add(fileName + " (modified)");
                }
            }
        }

        return res.toArray(new String[0]);
    }

    /** A helper method for Method Status to deal with the format problem. */
    private void statusPrintHelper(String title, String[] arr) {
        System.out.println("=== " + title + " ===");
        if (arr != null) {
            for (String str : arr) {
                System.out.println(str);
            }
        }
        System.out.println();
    }

    /** A helper method for map to the actual Checkout Method */
    public void checkout(String[] args) {
        final String split = "--";
        if (args.length == 2) {
            checkout(true, args[1]);
        } else if (args.length == 3 && args[1].equals(split)) {
            checkout(args[2]);
        } else if (args.length == 4 && args[2].equals(split)) {
            checkout(true, args[1], args[3]);
        } else {
            exitWithMsg("Incorrect operands.");
        }
    }

    /** An Actual Checkout Method by a commit and a file.
     *  Take a FILE from THE commit to overwrite the version of the work dir.
     */
    public void checkout(boolean isCommit, String commitId, String fileName) {
        if (!commits.hasCommit(commitId)) {
            exitWithMsg("No commit with that id exists.");
        }
        Cmt commit = commits.getCommit(commitId);
        if (!Commit.commitHasFile(commit, fileName)) {
            exitWithMsg("File does not exist in that commit.");
        }

        File cwdFile = join(CWD, fileName);
        String commitHashCode = Commit.getHashOfFile(commit, fileName);

        if (!cwdFile.exists() || !commitHashCode.equals(sha1(readContents(cwdFile)))) {
            byte[] content = blobs.getBlob(commitHashCode);
            blobs.saveBlob(cwdFile, content);
        }
    }

    /** An Actual Checkout Method by a file.
     *  Take the file from LAST commit to overwrite the version of the work dir.
     */
    public void checkout(String fileName) {
        checkout(true, branches.getHead(), fileName);
    }

    /** An Actual Checkout Method by a branch. */
    public void checkout(boolean isBranch, String branchName) {
        if (!branches.hasBranch(branchName)) {
            exitWithMsg("No such branch exists.");
        }
        if (branches.getCurrBranch().equals(branchName)) {
            exitWithMsg("No need to checkout the current branch.");
        }

        // checkout the files in the last commit;
        String branchPoint = branches.getBranchPoint(branchName);
        reset(branchPoint, branches.getHead());

        // set the point
        branches.setCurrBranch(branchName);
    }

    /** Add a new branch. */
    public void branch(String branchName) {
        if (branches.hasBranch(branchName)) {
            exitWithMsg("A branch with that name already exists.");
        }
        branches.setBranches(branchName, branches.getHead());
    }

    /** Rm a branch. */
    public void rmBranch(String branchName) {
        if (!branches.hasBranch(branchName)) {
            exitWithMsg("A branch with that name does not exist.");
        }
        if (branches.getCurrBranch().equals(branchName)) {
            exitWithMsg("Cannot remove the current branch.");
        }
        branches.removeBranch(branchName);
    }

    /**
     * Checks out all the files tracked by the given commit.
     * Removes tracked files that are not present in that commit.
     * Also moves the current branch’s head to that commit node.
     */
    public void reset(String commitHashCode) {
        reset(commitHashCode, branches.getHead());
        branches.setCurrentHead(commitHashCode);
    }

    /**
     * Checks out all the files tracked by the given commit.
     * Removes tracked files that are not present in that commit.
     * Also moves the current branch’s head to that commit node.
     */
    private void reset(String commitHashCode, String previousHashCode) {
        if (!commits.hasCommit(commitHashCode)) {
            exitWithMsg("No commit with that id exists.");
        }

        Cmt commit = commits.getCommit(commitHashCode);

        checkForUntrackedFiles(commit);

        String[] files = Commit.getFileNames(commit);

        if (files != null) {
            for (String file : files) {
                checkout(true, commitHashCode, file);
            }
        }

        // delete the files in previous commit but not in given commit.
        String[] lastFiles = Commit.getFileNames(commits.getCommit(previousHashCode));
        if (lastFiles != null) {
            for (String lastFile : lastFiles) {
                if (!Commit.commitHasFile(commit, lastFile)) {
                    restrictedDelete(join(CWD, lastFile));
                }
            }
        }

        stage.initStage();
    }

    /** Find the untracked file but in given commit, throw an error and quit the program. */
    private void checkForUntrackedFiles(Cmt commit) {
        String[] unTrackedFiles = getUnTrackedFiles();
        if (unTrackedFiles != null) {
            for (String unTrackedFile : unTrackedFiles) {
                if (Commit.commitHasFile(commit, unTrackedFile)) {
                    exitWithMsg("There is an untracked file in the way; "
                            + "delete it, or add and commit it first.");
                }
            }
        }
    }

    /** Merges files from the given branch into the current branch.
     *  Mainly deal with the situation that will NOT do a commit.
     */
    public void merge(String branchName) {
        checkForMerge(branchName);

        // get split point
        String givenPoint = branches.getBranchPoint(branchName);
        String headPoint = branches.getHead();
        String splitPoint = getSplitPoint(givenPoint, headPoint);

        // If the split point is the same commit as the given branch
        if (splitPoint.equals(givenPoint)) {
            exitWithMsg("Given branch is an ancestor of the current branch.");
        }

        // If the split point is the current branch
        if (splitPoint.equals(headPoint)) {
            checkout(true, branchName);
            exitWithMsg("Current branch fast-forwarded.");
        }

        merge(givenPoint, headPoint, splitPoint, branchName);
    }

    /** A helper method for Method Merge to do a commit. */
    private void merge(String givenPoint, String headPoint, String splitPoint,
                       String givenBranchName) {

        merge(givenPoint, headPoint, splitPoint);

        String message = "Merged " + givenBranchName + " into " + branches.getCurrBranch() + ".";
        commit(message);
        Commit.addParent(commits.getCommit(branches.getHead()), givenPoint);
        commits.saveCommits();
    }

    /** A helper method for Method Merge to do a commit. */
    private void merge(String givenPoint, String headPoint, String splitPoint) {
        Cmt givenCmt = commits.getCommit(givenPoint);
        Cmt headCmt = commits.getCommit(headPoint);
        Cmt splitCmt = commits.getCommit(splitPoint);

        String[] givenFiles = Commit.getFileNames(givenCmt);
        String[] splitFiles = Commit.getFileNames(splitCmt);

        if (splitFiles != null) {
            for (String file : splitFiles) {

                boolean isInGiven = Commit.commitHasFile(givenCmt, file);
                boolean isInHead = Commit.commitHasFile(headCmt, file);

                if (!isInHead) { // 1 & 2. file C&E - not in Both or not in Head, do nothing;
                    continue;
                }
                String splitVer = Commit.getHashOfFile(splitCmt, file);
                String headVer = Commit.getHashOfFile(headCmt, file);
                if (!isInGiven) {
                    if (splitVer.equals(headVer)) { // 3. file D only in Head, rm it;
                        rm(file);
                    } else { // 8. Conflict, file modified in HEAD & deleted in Given Branch.
                        blobs.mergeSingleBlob(file, headVer);
                        add(file);
                    }
                } else {  // in Both compare the version;
                    String givenVer = Commit.getHashOfFile(givenCmt, file);

                    if (headVer.equals(givenVer)) {
                        continue; // same version, do nothing;
                    }

                    if (splitVer.equals(headVer)) { // 4. fileA; overwrite with given version.
                        blobs.saveBlob(join(CWD, file), blobs.getBlob(givenVer));
                        add(file);
                    } else if (splitVer.equals(givenVer)) {  //5. fileB; ow with given version.
                        blobs.saveBlob(join(CWD, file), blobs.getBlob(headVer));
                        add(file);
                    } else { // conflict
                        blobs.mergeBlobs(file, headVer, givenVer);
                        add(file);
                    }
                }
            }
        }

        if (givenFiles != null) {
            for (String file : givenFiles) {
                boolean isSplit = Commit.commitHasFile(splitCmt, file);
                if (isSplit) { // Skip the file in split.
                    continue;
                }
                boolean isHead = Commit.commitHasFile(headCmt, file);
                String givenVer = Commit.getHashOfFile(givenCmt, file);
                if (!isHead) { // 6. File F; overwrite with given version, then add it.
                    blobs.saveBlob(join(CWD, file), blobs.getBlob(givenVer));
                    add(file);
                } else { // conflict;
                    blobs.mergeBlobs(file, Commit.getHashOfFile(headCmt, file), givenVer);
                    add(file);
                }
            }
        }
        // Skip the 7. File G only in Head, because we should do nothing.
    }

    /** A helper method for Method Merge to do some pre-check. */
    private void checkForMerge(String branchName) {
        if (!branches.hasBranch(branchName)) {
            exitWithMsg("A branch with that name does not exist.");
        }
        if (branches.getCurrBranch().equals(branchName)) {
            exitWithMsg("Cannot merge a branch with itself.");
        }
        checkForUntrackedFiles(commits.getCommit(branches.getBranchPoint(branchName)));

        if (stage.getFilesFromStage() != null || stage.getFilesFromRemovedStage() != null) {
            exitWithMsg("You have uncommitted changes.");
        }
    }

    /** Return the Hash Code of the split point of the given commit and the HEAD. BFS solution*/
    private String getSplitPoint(String firstHashCode, String secondHashCode) {
        HashSet<String> visited = new HashSet<>();

        Deque<String> deque = new ArrayDeque<>();
        deque.addLast(firstHashCode);
        deque.addLast(secondHashCode);

        while (!deque.isEmpty()) {
            String curr = deque.removeFirst();
            if (visited.contains(curr)) {
                return curr;
            }
            visited.add(curr);
            for (String parent : Commit.getParents(commits.getCommit(curr))) {
                if (parent != null) {
                    deque.addLast(parent);
                }
            }
        }

        throw error("There is Not a split point! Something is error!");
    }

    /** Exit program with message */
    public static void exitWithMsg(String message) {
        System.out.println(message);
        System.exit(0);
    }
}
