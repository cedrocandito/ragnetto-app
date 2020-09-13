package it.davideorlandi.ragnetto;

public class RagnettoConstants
{
    public static final int NUM_LEGS = 6;
    public static final int NUM_JOINTS = 3;

    /** Command code to request a configuration dump. */
    public static final String COMMAND_REQUEST_CONFIGURATION = "C";

    /** Command code to change mode. */
    public static final String COMMAND_MODE = "M";

    /** Command code to change height offset. */
    public static final String COMMAND_HEIGHT_OFFSET = "H";

    /** Command code to change lift height. */
    public static final String COMMAND_LIFT_HEIGHT = "L";

    /** Command code to change max phase duration. */
    public static final String COMMAND_MAX_PHASE_DURATION = "P";

    /** Command code to change leg lift and drop duration. */
    public static final String COMMAND_LEG_LIFT_AND_DROP_DURATION = "Q";

    /** Command code to change trim values. */
    public static final String COMMAND_TRIM = "T";

    /** Command code to read configuration from EEPROM. */
    public static final String COMMAND_READ = "R";

    /** Command code to write configuration to EEPROM. */
    public static final String COMMAND_WRITE = "W";

    /** Line type of configuration dump. */
    public static final char LINE_TYPE_CONFIGURATION = 'C';
}
