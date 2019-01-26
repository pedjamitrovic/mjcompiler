package rs.ac.bg.etf.pp1;
import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.ac.bg.etf.pp1.CounterVisitor.ActParamCounter;

import java.util.Collection;
import java.util.Iterator;
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
		Tab.openScope();
	}

	public void visit(ProgramNode node){
		staticVarCount = Tab.currentScope.getnVars();
		Tab.chainLocalSymbols(node.getProgramDecl().obj);
		Tab.closeScope();
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
		else report_error("Error: Invalid use of dot operator ", node);
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
		if(node.getBoxesOpt() instanceof BoxesNode){
			Struct arrayType = TabExtension.resolveArrayType(currentVarType.obj.getType());
			node.obj = Tab.insert(Obj.Var, node.getVarName(), arrayType);
		}
		else{
			if(currentVarType.obj.getType() != TabExtension.enumType){
				node.obj = Tab.insert(Obj.Var, node.getVarName(), currentVarType.obj.getType());
			}
			else{
				node.obj = Tab.insert(Obj.Var, node.getVarName(), Tab.intType);
			}
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
        Tab.openScope();
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
		node.obj = Tab.insert(Obj.Con, node.getEnumConstName(), Tab.intType);
		node.obj.setAdr(value);
    }
	public void visit(EnumNode node){
		Tab.chainLocalSymbols(currentEnum);
		Tab.closeScope();
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
			node.obj = new Obj(Obj.NO_VALUE, "$newArray", TabExtension.resolveArrayType(node.getType().obj.getType()));
		}
    	else{
    		// KLASA....
		}
	}
	// ########## [E] Expressions ##########

	// ########## [S] Methods ##########
	Obj currentMethod = null;
	Obj currentMethodCall = null;
	boolean returnFound = false;
	int currFpPos = 0;
	Stack<MethodCallContext> methodCallContextStack = new Stack<MethodCallContext>();
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
		Tab.openScope();
		returnFound = false;
		currFpPos = 0;
	}
	public void visit(MethodNode node){
		if(currentMethod.getType() != Tab.noType && !returnFound){
			report_error("Error: Method " + currentMethod.getName() + " doesn't have return statement ", node);
		}
		Tab.chainLocalSymbols(currentMethod);
		Tab.closeScope();
		currentMethod.setLevel(currFpPos);
		currentMethod = null;
		currFpPos = 0;
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
		if(node.getBoxesOpt() instanceof BoxesNode){
			type = TabExtension.resolveArrayType(type);
		}
		if(type == TabExtension.enumType){
			type = Tab.intType;
		}
		Obj parameter = Tab.insert(Obj.Var, node.getParName(), type);
		parameter.setFpPos(++currFpPos);
	}
	public void visit(MethodCallDeclNode node){
		node.obj = node.getDesignator().obj;
		currentMethodCall = node.obj;
		methodCallContextStack.push(new MethodCallContext());
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
}

