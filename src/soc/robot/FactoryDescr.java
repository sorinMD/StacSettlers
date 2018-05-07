package soc.robot;

public class FactoryDescr {
    public int count;
    public String name;
    public SOCRobotFactory factory;	

    public FactoryDescr(SOCRobotFactory f, String n, int c) {
        factory = f;
        name = n;
        count = c;
    }

    public FactoryDescr copy() {
        return new FactoryDescr(factory, name, count);
    }
}
