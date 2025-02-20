package gitlet;

import static gitlet.Utils.error;

/** Driver class for Gitlet, a subset of the Git version-control system.
 * It takes in arguments from the command line and based on the command
 * (the first element of args array) calls the corresponding command in Repository 
 * which will actually execute the logic of the command.
 * It also validates the arguments based on the command to ensure 
 * that enough arguments were passed in.
 * 
 *  @author Daniel Feng
 */
public class Main {

    /**
     * Call methods in Class REPOSITORY after checking the number of ARGS.
     * Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        if (args.length == 0) { // if NO command
            Repository.exitWithMsg("Please enter a command.");
        }

        switch (args[0]) {
            case "init" -> {
                validateNumArgs(args, 1);
                Repository.init();
            }
            case "add" -> {
                validateNumArgs(args, 2);
                Repository.fromFile().add(args[1]);
            }
            case "commit" -> {
                validateNumArgs(args, 2);
                Repository.fromFile().commit(args[1]);
            }
            case "rm" -> {
                validateNumArgs(args, 2);
                Repository.fromFile().rm(args[1]);
            }
            case "log" -> {
                validateNumArgs(args, 1);
                Repository.fromFile().log();
            }
            case "global-log" -> {
                validateNumArgs(args, 1);
                Repository.fromFile().globalLog();
            }
            case "find" -> {
                validateNumArgs(args, 2);
                Repository.fromFile().find(args[1]);
            }
            case "status" -> {
                validateNumArgs(args, 1);
                Repository.fromFile().status();
            }
            case "checkout" -> {
                if (checkNumArgs(args, 2) || checkNumArgs(args, 3) || checkNumArgs(args, 4)) {
                    Repository.fromFile().checkout(args);
                } else {
                    validateNumArgs(args, 1);
                }
            }
            case "branch" -> {
                validateNumArgs(args, 2);
                Repository.fromFile().branch(args[1]);
            }
            case "rm-branch" -> {
                validateNumArgs(args, 2);
                Repository.fromFile().rmBranch(args[1]);
            }
            case "reset" -> {
                validateNumArgs(args, 2);
                Repository.fromFile().reset(args[1]);
            }
            case "merge" -> {
                validateNumArgs(args, 2);
                Repository.fromFile().merge(args[1]);
            }
            default -> Repository.exitWithMsg("No command with that name exists.");
        }
    }

    /**
     * Checks the number of arguments versus the expected number,
     * Exit if they do not match.
     *
     * @param args Argument array from command line
     * @param n Number of expected arguments
     */
    public static void validateNumArgs(String[] args, int n) {
        if (!checkNumArgs(args, n)) {
            throw error("Incorrect operands.");
        }
    }

    /**
     * Checks the number of arguments versus the expected number,
     *
     * @param args Argument array from command line
     * @param n Number of expected arguments
     */
    private static boolean checkNumArgs(String[] args, int n) {
        if (args.length != n) {
            return false;
        }
        for (int i = 1; i < n; i++) {
            if (args[i] == null) {
                return false;
            }
        }
        return true;
    }
}
