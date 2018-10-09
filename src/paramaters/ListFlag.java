package paramaters;

import java.util.List;

/**
 * This class is a Flag that only accepts arugments that are contained in a list.
 */
public class ListFlag extends Flag<String> {

    private List<String> list;

    /**
     * Constructs a new ListFlag with the given properties and a list of accepted arguments
     *
     * @param name the name of flag
     * @param flag the short-hand name of the flag
     * @param description the description of the flag
     * @param list the list of accepted arguments
     * @param required whether or not the flag is required
     */
    public ListFlag(String name, String flag, String description, List<String> list, boolean required) {
        super(name, flag, description, required);
        this.list = list;
    }

    /**
     * Constructs a new ListFlag with the given properties and a list of accepted arguments
     *
     * @param name the name of flag
     * @param flag the short-hand name of the flag
     * @param description the description of the flag
     * @param list the list of accepted arguments
     */
    public ListFlag(String name, String flag, String description, List<String> list) {
        this(name, flag, description, list, false);
    }

    @Override
    public boolean expectsArgument() {
        return true;
    }

    @Override
    protected String parseArgument(String arg) {
        if (!list.contains(arg)) {
            String string = "Invalid option for [" + getName() + "]\n";
            string += "\tValid options:";
            for (String s : list) {
                string += s + "\n\t\t";
            }
            throw new ParamaterException(string);
        }
        return arg;
    }

}