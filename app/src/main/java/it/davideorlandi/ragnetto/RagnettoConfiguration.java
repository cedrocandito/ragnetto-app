package it.davideorlandi.ragnetto;

public class RagnettoConfiguration
{
    public byte commandMode = 3;
    public byte[][] trim = new byte[RagnettoConstants.NUM_LEGS][RagnettoConstants.NUM_JOINTS];
    public byte heightOffset = 0;
    public short liftHeight = 25;    // 0-255
    public short maxPhaseDuration = 200;
    public byte legLiftDurationPercent = 20;
    public byte legDropDurationPercent = 20;

    /**
     * Create a RagnettoConfiguration using a configuration line ("C" followed by ";"-separated
     * numbers).
     *
     * @param line source configuration line.
     * @return new object created, or null if it if not a valid configuration line.
     */
    public static final RagnettoConfiguration parseLine(String line)
    {
        if (line.startsWith("C;"))
        {
            RagnettoConfiguration c = new RagnettoConfiguration();
            String[] parts = line.substring(2).split(";", 0);
            int l = parts.length;
            if (l >= 1)
                c.commandMode = Byte.parseByte(parts[0]);
            if (l >= 19)
            {
                for (int i = 0; i < RagnettoConstants.NUM_LEGS; i++)
                {
                    for (int j = 0; j < RagnettoConstants.NUM_JOINTS; j++)
                    {
                        c.trim[i][j] = Byte.parseByte(parts[i * 3 + j + 1]);
                    }
                }
            }
            if (l >= 20)
                c.heightOffset = Byte.parseByte(parts[19]);
            if (l >= 21)
            {
                c.liftHeight = Short.parseShort(parts[20]);
            }
            if (l >= 22)
                c.maxPhaseDuration = Short.parseShort(parts[21]);
            if (l >= 23)
                c.legLiftDurationPercent = Byte.parseByte(parts[22]);
            if (l >= 24)
                c.legDropDurationPercent = Byte.parseByte(parts[23]);
            return c;
        }
        else
        {
            return null;
        }
    }
}
