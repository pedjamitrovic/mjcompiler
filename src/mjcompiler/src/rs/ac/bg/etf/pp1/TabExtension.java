package rs.ac.bg.etf.pp1;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

public class TabExtension {
    public static HashMap<Struct, Struct> arrayTypes = new HashMap<>();
    public static HashMap<String, Struct> classTypes = new HashMap<>();
    public static HashMap<String, Struct> interfaceTypes = new HashMap<>();
    public static Stack<Integer> currentKindStack = new Stack<>();
    public static final Struct boolType = new Struct(5);
    public static final Struct enumType = new Struct(6);
    public static int currTypeCounter = 6;

    public static boolean AssignableTo(Struct src, Struct dst){
        if(src.assignableTo(dst)) return true;
        Struct currSrc = src;
        while(currSrc != null){
            if(currSrc.equals(dst)) return true;
            Obj extendedClass = null;
            Iterator<Obj> currSrcSymbolsIterator = currSrc.getMembers().symbols().iterator();
            while(currSrcSymbolsIterator.hasNext()){
                Obj currSymbol = currSrcSymbolsIterator.next();
                if(currSymbol.getKind() != Obj.Type) continue;
                if(currSymbol.getName().equals("$extendsType")) {
                    extendedClass = currSymbol;
                    continue;
                }
                if(currSymbol.getName().startsWith("$implementsType-")){
                    if(currSymbol.getType().equals(dst)) return true;
                }
            }
            currSrc = (extendedClass != null ? extendedClass.getType() : null);
        }
        return false;
    }

    public static void init(){
        Tab.currentScope().addToLocals(new Obj(Obj.Type, "bool", boolType));
    }
    public static Struct resolveArrayType(Struct elemType){
        Struct arrayType = arrayTypes.get(elemType);
        if(arrayType == null){
            arrayType = new Struct(Struct.Array, elemType);
            arrayTypes.put(elemType, arrayType);
        }
        return arrayType;
    }
    public static Struct resolveClassType(String className){
        Struct type = classTypes.get(className);
        if(type == null){
            type = new Struct(Struct.Class, new Struct(++currTypeCounter));
            classTypes.put(className, type);
        }
        return type;
    }
    public static Struct findClassType(String className){
        Struct type = classTypes.get(className);
        if(type == null){
            type = Tab.noType;
        }
        return type;
    }
    public static Struct resolveInterfaceType(String interfaceName){
        Struct type = interfaceTypes.get(interfaceName);
        if(type == null){
            type = new Struct(Struct.Class, new Struct(++currTypeCounter));
            interfaceTypes.put(interfaceName, type);
        }
        return type;
    }
    public static Struct findInterfaceType(String interfaceName){
        Struct type = interfaceTypes.get(interfaceName);
        if(type == null){
            type = Tab.noType;
        }
        return type;
    }
    public static boolean checkIfClassOrInterface(Struct type){
        boolean ret = false;
        if(classTypes.containsValue(type)) ret = true;
        if(interfaceTypes.containsValue(type)) ret = true;
        return ret;
    }
    public static void openScope(int kind){
        Tab.openScope();
        currentKindStack.push(kind);
    }
    public static void closeScope(){
        currentKindStack.pop();
        Tab.closeScope();
    }
    public static Obj insert(String name, Struct type){
        return Tab.insert(currentKindStack.peek(), name, type);
    }
}
