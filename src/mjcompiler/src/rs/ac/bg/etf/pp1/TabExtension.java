package rs.ac.bg.etf.pp1;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class TabExtension {
    public static final Struct boolType = new Struct(5);
    public static final Struct enumType = new Struct(6);

    public static void init(){
        Tab.currentScope().addToLocals(new Obj(Obj.Type, "bool", boolType));
    }
}
