package rs.ac.bg.etf.pp1;
import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.ac.bg.etf.pp1.CounterVisitor.ActParamCounter;
import rs.etf.pp1.symboltable.structure.SymbolDataStructure;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Stack;

public class SemanticPass extends VisitorAdaptor {

	Logger log = Logger.getLogger(getClass());

	boolean errorDetected = false;
	public boolean passed() {
		return !errorDetected;
	}

	public void report_error(String message, SyntaxNode info) {
		errorDetected = true;
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0: info.getLine();
		if (line != 0)
			msg.append ("on line ").append(line);
		log.error(msg.toString());
	}

	public void report_info(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message); 
		int line = (info == null) ? 0: info.getLine();
		if (line != 0)
			msg.append ("on line ").append(line);
		log.info(msg.toString());
	}

	public boolean checkIfSymbolExists(String symbolName){
		Obj obj = Tab.currentScope.findSymbol(symbolName);
		return obj != null && obj != Tab.noObj;
	}

	// ########## [S] Program ##########
	int staticVarCount;

	public void visit(ProgramDeclNode node){
		node.obj = Tab.insert(Obj.Prog, node.getProgramName(), Tab.noType);
		TabExtension.openScope(Obj.Var);
	}

	public void visit(ProgramNode node){
		if(!mainFound) report_error("Function main not found in program ", null);
		staticVarCount = Tab.currentScope.getnVars();
		Tab.chainLocalSymbols(node.getProgramDecl().obj);
		TabExtension.closeScope();
	}
	// ########## [E] Program ##########

	// ########## [S] Types ##########
	public void visit(TypeNode node) {
		Obj typeObj = Tab.find(node.getTypeName());
		node.obj = Tab.noObj;
		if (typeObj == Tab.noObj) {
			report_error("Type " + node.getTypeName() + " not found ", null);
		}
		else {
			if (typeObj.getKind() == Obj.Type) {
				node.obj = typeObj;
			}
			else {
				report_error("Error: Name " + node.getTypeName() + " doesn't represent type ", node);
			}
		}
	}
	// ########## [E] Types ##########

	// ########## [S] Designator ##########
	public void visit(DesignatorNode node){
		Obj obj = Tab.find(node.getName());
		if (obj == Tab.noObj) {
			report_error("Error: Name " + node.getName() + " has not been declared ", node);
		}
		node.obj = obj;
	}
	public void visit(DesignatorChainNode node){
		node.obj = Tab.noObj;
		if(node.getDesignator().obj.getType() == TabExtension.enumType){
			Iterator<Obj>  it = node.getDesignator().obj.getLocalSymbols().iterator();
			boolean enumConstFound = false;
			while(it.hasNext() && !enumConstFound){
				Obj localSymbol = it.next();
				if(localSymbol.getName().equals(node.getName())){
					enumConstFound = true;
					node.obj = localSymbol;
				}
			}
			if(!enumConstFound){
				report_error("Error: Enum " + node.getDesignator().obj.getName() + " doesn't have defined value for " + node.getName() + " ", node);
			}
		}
		else {
			Struct designatorType = node.getDesignator().obj.getType();
			if(designatorType.getKind() != Struct.Class){
				report_error("Error: Designator of dot operator must be variable of class type or enum type ", node);
			}
			Obj obj = designatorType.getMembers().searchKey(node.getName());
			if(obj == null){
				report_error("Error: Member " + node.getName() + " doesn't exist ", node);
			}
			else node.obj = obj;
		}
	}
	public void visit(DesignatorIndexNode node){
		if(node.getDesignator().obj.getType().getKind() != Struct.Array){
			report_error("Error: Indexing must be done on array type ", node);
		}
		if(node.getExpr().obj.getType() != Tab.intType){
			report_error("Error: Index of array must be int ", node);
		}
		node.obj = new Obj(Obj.Elem, "$elem", node.getDesignator().obj.getType().getElemType());
	}
	public void visit(ReadStmtNode node){
		Designator readDesignator = node.getDesignator();
		if(!(readDesignator.obj.getType() == Tab.intType || readDesignator.obj.getType() == Tab.charType|| readDesignator.obj.getType() == TabExtension.boolType)){
			report_error("Error: Parameter of read function must be int, char or bool ", node);
		}
	}
	// ########## [E] Designator ##########

	// ########## [S] Constants ##########
	TypeNode currentConstType;
	public void visit(ConstSectDeclNode node){
		currentConstType = (TypeNode) node.getType();
	}
	public void visit(ConstDeclNode node){
		if(checkIfSymbolExists(node.getConstName())){
			report_error("Error: Name " + node.getConstName() + " already defined ", node);
			node.obj = Tab.noObj;
			return;
		}
		ConstValue value = node.getConstValue();
		Struct constType = Tab.noType;
		int constValue = 0;
		if(value instanceof NumConstNode && currentConstType.obj.getType() == Tab.intType){
			NumConstNode numConstNode = (NumConstNode) value;
			constValue = numConstNode.getValue();
			constType = Tab.intType;
		}
		else if(value instanceof CharConstNode && currentConstType.obj.getType() == Tab.charType){
			CharConstNode charConstNode = (CharConstNode) value;
			constValue = (int) charConstNode.getValue();
			constType = Tab.charType;
		}
		else if(value instanceof BoolConstNode && currentConstType.obj.getType() == TabExtension.boolType){
			BoolConstNode boolConstNode = (BoolConstNode) value;
			constValue = boolConstNode.getValue() ? 1 : 0;
			constType = TabExtension.boolType;
		}
		else report_error("Error: Constant type and constant value mismatch ", node);
		node.obj = Tab.insert(Obj.Con, node.getConstName(), constType);
		node.obj.setAdr(constValue);
	}
	// ########## [E] Constants ##########

	// ########## [S] Variables ##########
	TypeNode currentVarType;
	public void visit(VarSectDeclNode node){
		currentVarType = (TypeNode) node.getType();
	}
	public void visit(VarDeclNode node){
		if(checkIfSymbolExists(node.getVarName())){
			report_error("Error: Name " + node.getVarName() + " already defined ", node);
			node.obj = Tab.noObj;
			return;
		}
		Struct type = currentVarType.obj.getType();
		if(type == TabExtension.enumType){
			type = Tab.intType;
		}
		if(node.getBoxesOpt() instanceof BoxesNode){
			Struct arrayType = TabExtension.resolveArrayType(type);
			node.obj = TabExtension.insert(node.getVarName(), arrayType);
		}
		else{
			node.obj = TabExtension.insert(node.getVarName(), type);
		}
	}
	// ########## [E] Variables ##########

    // ########## [S] Enums ##########
    Obj currentEnum;
    int currEnumVal = 0;
    public void visit(EnumDeclNode node){
        if(checkIfSymbolExists(node.getEnumName())){
            report_error("Error: Name " + node.getEnumName() + " already defined ", node);
            node.obj = Tab.noObj;
        }
        else{
            node.obj = Tab.insert(Obj.Type, node.getEnumName(), TabExtension.enumType);
        }
        currentEnum = node.obj;
        TabExtension.openScope(Obj.Con);
		currEnumVal = 0;
    }
    public void visit(EnumConstDeclNode node){
        if(checkIfSymbolExists(node.getEnumConstName())){
            report_error("Error: Name " + node.getEnumConstName() + " already defined ", node);
            node.obj = Tab.noObj;
            return;
        }
        int value;
        OptEnumConstValue enumConstValue = node.getOptEnumConstValue();
        if(enumConstValue instanceof OptEnumConstValueNode){
			OptEnumConstValueNode optEnumConstValueNode = (OptEnumConstValueNode) enumConstValue;
			if(optEnumConstValueNode.getValue() > currEnumVal){
				value = optEnumConstValueNode.getValue();
				currEnumVal = value + 1;
			}
			else{
				value = currEnumVal++;
			}
        }
        else{
			value = currEnumVal++;
		}
		node.obj = TabExtension.insert(node.getEnumConstName(), Tab.intType);
		node.obj.setAdr(value);
    }
	public void visit(EnumNode node){
		Tab.chainLocalSymbols(currentEnum);
		TabExtension.closeScope();
		currentEnum = null;
		currEnumVal = 0;
	}
    // ########## [E] Enums ##########

	// ########## [S] Expressions ##########
	public void visit(FactorConstNode node){
		ConstValue value = node.getConstValue();
		Struct constType = Tab.noType;
		int constValue = 0;
		if(value instanceof NumConstNode){
			NumConstNode numConstNode = (NumConstNode) value;
			constValue = numConstNode.getValue();
			constType = Tab.intType;
		}
		else if(value instanceof CharConstNode){
			CharConstNode charConstNode = (CharConstNode) value;
			constValue = (int) charConstNode.getValue();
			constType = Tab.charType;
		}
		else if(value instanceof BoolConstNode){
			BoolConstNode boolConstNode = (BoolConstNode) value;
			constValue = boolConstNode.getValue()? 1 : 0;
			constType = TabExtension.boolType;
		}
		node.obj = new Obj(Obj.Con, "$", constType);
		node.obj.setAdr(constValue);
	}
	public void visit(FactorDesignatorNode node){
        node.obj = node.getDesignator().obj;
        if(node.obj.getKind() == Obj.Meth){
            report_error("Error: Name " + node.obj.getName() + " is a method, not variable ", node);
        }
	}
	public void visit(FactorExprNode node){
		node.obj = node.getExpr().obj;
	}
	public void visit(TermNode node){
		node.obj = node.getFactor().obj;
	}
	public void visit(TermMulopNode node){
		if(node.getTerm().obj.getType() == Tab.intType && node.getFactor().obj.getType() == Tab.intType){
			node.obj = new Obj(Obj.NO_VALUE, "$", Tab.intType);
		}
		else{
			report_error("Error: Multiplication/Division/Modulo done on something other than int ", node);
			node.obj = Tab.noObj;
		}
	}
	public void visit(ExprNode node){
		node.obj = node.getTerm().obj;
	}
	public void visit(ExprAddopNode node){
		if(node.getExpr().obj.getType() == Tab.intType && node.getTerm().obj.getType() == Tab.intType){
			node.obj = new Obj(Obj.NO_VALUE, "$", Tab.intType);
		}
		else{
			report_error("Error: Addition/Subtraction done on something other than int ", node);
			node.obj = Tab.noObj;
		}
	}
	public void visit(DesignatorStmtAssignNode node){
		if(!node.getExpr().obj.getType().assignableTo(node.getDesignator().obj.getType())){
			report_error("Error: Source is not assignable to destination ", node);
		}
	}
	public void visit(FactorCallNode node){
		node.obj = node.getMethodCall().obj;
	}
	public void visit(FactorNewNode node){
    	if(node.getOptNewArray() instanceof OptNewArrayNode){
			OptNewArrayNode optNewArrayNode = (OptNewArrayNode)node.getOptNewArray();
			if(optNewArrayNode.getExpr().obj.getType() != Tab.intType){
				report_error("Error: Number of array elements must be int type ", node);
			}
			Struct type = node.getType().obj.getType();
			if(type == TabExtension.enumType){
				type = Tab.intType;
			}
			node.obj = new Obj(Obj.NO_VALUE, "$newArray", TabExtension.resolveArrayType(type));
		}
    	else{
			Struct type = TabExtension.findClassType(node.getType().obj.getName());
			if(type == Tab.noType){
				report_error("Error: Class name must be provided after keyword new ", node);
				node.obj = Tab.noObj;
			}
			else node.obj = new Obj(Obj.Var, "$newType", type);
		}
	}
	public void visit(CondFactRelopNode node){
    	if(!node.getExpr().obj.getType().compatibleWith(node.getExpr1().obj.getType())){
			report_error("Error: Cannot compare incompatible types ", node);
		}
	}
	// ########## [E] Expressions ##########

	// ########## [S] Methods ##########
	Obj currentMethod = null;
	Obj currentMethodCall = null;
	boolean returnFound = false;
	boolean mainFound = false;
	int currFpPos = 0;
	Stack<MethodCallContext> methodCallContextStack = new Stack<>();
	static class MethodCallContext{
		public int currActParNum = 0;
	}
	public void visit(MethodDeclNode node){
		if(checkIfSymbolExists(node.getMethodName())){
			report_error("Error: Name " + node.getMethodName() + " already defined ", node);
			node.obj = Tab.noObj;
		}
		else{
			Struct retType = Tab.noType;
			if(node.getRetType() instanceof RetTypeNode){
				RetTypeNode typeNode = (RetTypeNode) node.getRetType();
				retType = typeNode.getType().obj.getType();
			}
			if(retType == TabExtension.enumType){
				retType = Tab.intType;
			}
			node.obj = Tab.insert(Obj.Meth, node.getMethodName(), retType);
		}
		currentMethod = node.obj;
		TabExtension.openScope(Obj.Var);
		returnFound = false;
		currFpPos = 0;
		if(currentClass != null || currentInterface != null){
			Struct currType = currentClass != null ? currentClass.getType() : currentInterface.getType();
			Obj parameter = TabExtension.insert("this", currType);
			parameter.setFpPos(++currFpPos);
		}
	}
	public void visit(MethodNode node){
		if(currentMethod.getName().equals("main")){
			if(currentMethod.getType() != Tab.noType) report_error("Error: Function \"main\" must have return type void ", node);
			mainFound = true;
		}
		if(currentMethod.getType() != Tab.noType && !returnFound){
			report_error("Error: Method " + currentMethod.getName() + " doesn't have return statement ", node);
		}
		Tab.chainLocalSymbols(currentMethod);
		TabExtension.closeScope();
		currentMethod = null;
		currFpPos = 0;
	}
	public void visit(MethodSignatureNode node){
		currentMethod.setLevel(currFpPos);
		Tab.chainLocalSymbols(currentMethod);
		if(currentInterface != null){
			TabExtension.closeScope();
		}
	}
	public void visit(ReturnStmtNode node){
		returnFound = true;
		OptExpr optExpr = node.getOptExpr();
		if(optExpr instanceof OptExprNode){
			if(currentMethod.getType() != Tab.noType){
				OptExprNode optExprNode = (OptExprNode) optExpr;
				if(!optExprNode.getExpr().obj.getType().assignableTo(currentMethod.getType())){
					report_error("Error: Return expression type doesn't match method return type ", node);
				}
			}
			else {
				report_error("Error: Cannot return an expression on method with return type void ", node);
			}
		}
		else if (optExpr instanceof NoOptExprNode){
			if(currentMethod.getType() != Tab.noType){
				report_error("Error: Return statement must have expression to return ", node);
			}
		}
	}
	public void visit(MethodCallNode node){
		if(currentMethodCall.getKind() != Obj.Meth){
			report_error("Error: Name " + currentMethodCall.getName() + " is not a function ", node);
			node.obj = Tab.noObj;
		}
		else {
			node.obj = currentMethodCall;
		}
		if(methodCallContextStack.peek().currActParNum != currentMethodCall.getLevel()){
			report_error("Error: Number of actual parameters is different than number of formal parameters ", node);
		}
		methodCallContextStack.pop();
	}
	public void visit(FormParDeclNode node){
		Struct type = node.getType().obj.getType();
		if(type == TabExtension.enumType){
			type = Tab.intType;
		}
		if(node.getBoxesOpt() instanceof BoxesNode){
			type = TabExtension.resolveArrayType(type);
		}
		Obj parameter = TabExtension.insert(node.getParName(), type);
		parameter.setFpPos(++currFpPos);
	}
	public void visit(MethodCallDeclNode node){
		node.obj = node.getDesignator().obj;
		currentMethodCall = node.obj;
		methodCallContextStack.push(new MethodCallContext());
		if(node.getDesignator() instanceof DesignatorChainNode || currentClass != null){
            if(!node.obj.getName().equals("ord") && !node.obj.getName().equals("chr") && !node.obj.getName().equals("len")) methodCallContextStack.peek().currActParNum++;
        }
	}
	public void visit(ActParDeclNode node){
		methodCallContextStack.peek().currActParNum++;
		Iterator<Obj> it = currentMethodCall.getLocalSymbols().iterator();
		while(it.hasNext()){
			Obj localSymbol = it.next();
			if(localSymbol.getFpPos() == methodCallContextStack.peek().currActParNum){
				if(!node.getExpr().obj.getType().assignableTo(localSymbol.getType())){
					report_error("Error: Actual parameter is not assignable to formal parameter ", node);
				}
			}
		}
	}
	// ########## [E] Methods ##########

	// ########## [E] Classes ##########
	Obj currentClass = null;
	LinkedList<Obj> currentClassImplementsList = new LinkedList<>();
	public void visit(ClassDeclNode node){
		node.obj = Tab.noObj;
		if(checkIfSymbolExists(node.getName())){
			report_error("Error: Name " + node.getName() + " already defined ", node);
		}
		else{
			node.obj = Tab.insert(Obj.Type, node.getName(), TabExtension.resolveClassType(node.getName()));
		}
		currentClass = node.obj;
		TabExtension.openScope(Obj.Fld);
		Tab.chainLocalSymbols(currentClass.getType());
	}
	public void visit(ClassDefNode node){
		Tab.chainLocalSymbols(currentClass.getType());
		TabExtension.closeScope();
		CheckImplementsList();
		currentClass = null;
		currentClassImplementsList.clear();
	}
	public void visit(FieldSectListNode node){
        Tab.chainLocalSymbols(currentClass.getType());
    }
    public void visit(ImplementsTypeNode node){
		if(TabExtension.findInterfaceType(node.getType().obj.getName()) == Tab.noType){
			if(node.getType().obj != Tab.noObj) report_error("Error: Class " + currentClass.getName() + " implements " + node.getType().obj.getName() + " which is not an interface ", null);
		}
		else {
			currentClass.getType().getMembers().insertKey(Tab.find(node.getType().obj.getName()));
			currentClassImplementsList.add(Tab.find(node.getType().obj.getName()));
		}
	}
    private void CheckImplementsList(){
		Iterator<Obj> interfaceIterator = currentClassImplementsList.iterator();
		while(interfaceIterator.hasNext()){
			Obj currentInterface = interfaceIterator.next();
			Iterator<Obj> interfaceMethodIterator = currentInterface.getType().getMembers().symbols().iterator();
			while(interfaceMethodIterator.hasNext()){
				Obj currentInterfaceMethod = interfaceMethodIterator.next();
				Iterator<Obj> currentClassMethodIterator = currentClass.getType().getMembers().symbols().iterator();
				boolean foundInterfaceMethod = false;
				while(currentClassMethodIterator.hasNext() && !foundInterfaceMethod){
					Obj currentClassMethod = currentClassMethodIterator.next();
					if(currentClassMethod.getKind() != Obj.Meth) continue;
					if(currentClassMethod.getName().equals(currentInterfaceMethod.getName())){
						foundInterfaceMethod = true;
						if(currentClassMethod.getLevel() != currentInterfaceMethod.getLevel()){
							report_error("Error: Method " + currentClassMethod.getName() + " in class " + currentClass.getName() + " doesn't have same number of formal parameters as in implemented interface " + currentInterface.getName() + " ", null);
						}
						else{
							Iterator<Obj> currentInterfaceMethodParamIterator = currentInterfaceMethod.getLocalSymbols().iterator();
							Iterator<Obj> currentClassMethodParamIterator = currentClassMethod.getLocalSymbols().iterator();
							boolean foundWrongParamType = false;
							while(currentInterfaceMethodParamIterator.hasNext() && !foundWrongParamType){
								Obj currentInterfaceMethodParam = currentInterfaceMethodParamIterator.next();
								Obj currentClassMethodParam = currentClassMethodParamIterator.next();
								if(currentInterfaceMethodParam.getName().equals("this")) continue;
								if(!currentInterfaceMethodParam.getType().equals(currentClassMethodParam.getType())){
									foundWrongParamType = true;
									report_error("Error: Method " + currentClassMethod.getName() + " in class " + currentClass.getName() + " doesn't have same type of formal parameters as in implemented interface " + currentInterface.getName() + " ", null);
								}
							}
						}
						if(!currentClassMethod.getType().assignableTo(currentInterfaceMethod.getType())){
							report_error("Error: Method " + currentClassMethod.getName() + " in class " + currentClass.getName() + " doesn't have assignable return type to type defined in implemented interface " + currentInterface.getName() + " ", null);
						}
					}
				}
				if(!foundInterfaceMethod){
					report_error("Error: Method " + currentInterfaceMethod.getName() + " is not implemented in class " + currentClass.getName() + " ", null);
				}
			}
		}
	}
	// ########## [E] Classes ##########

	// ########## [E] Interfaces ##########
	Obj currentInterface = null;
	public void visit(InterfaceDeclNode node){
		node.obj = Tab.noObj;
		if(checkIfSymbolExists(node.getName())){
			report_error("Error: Name " + node.getName() + " already defined ", node);
		}
		else{
			node.obj = Tab.insert(Obj.Type, node.getName(), TabExtension.resolveInterfaceType(node.getName()));
		}
        currentInterface = node.obj;
		Tab.openScope();
		Tab.chainLocalSymbols(currentInterface.getType());
	}
	public void visit(InterfaceDefNode node){
		Tab.chainLocalSymbols(currentInterface.getType());
		Tab.closeScope();
        currentInterface = null;
	}
	// ########## [E] Interfaces ##########
}

