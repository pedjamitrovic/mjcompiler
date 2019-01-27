package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.CounterVisitor.FormParamCounter;
import rs.ac.bg.etf.pp1.CounterVisitor.VarCounter;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.ArrayList;
import java.util.Stack;

public class CodeGenerator extends VisitorAdaptor {
	
	private int mainPc;
	
	public int getMainPc() {
		return mainPc;
	}

	public void visit(MethodDeclNode node) {
		if (node.getMethodName().equals("main")) {
			mainPc = Code.pc;
		}
		node.obj.setAdr(Code.pc);

		// Collect arguments and local variables.
		SyntaxNode methodNode = node.getParent().getParent();
		VarCounter varCnt = new VarCounter();
		methodNode.traverseTopDown(varCnt);
		FormParamCounter fpCnt = new FormParamCounter();
		methodNode.traverseTopDown(fpCnt);

		// Generate the entry.
		Code.put(Code.enter);
		Code.put(fpCnt.getCount());
		Code.put(varCnt.getCount() + fpCnt.getCount());
	}

	public void visit(ReturnStmtNode node) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	public void visit(MethodNode node) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	public void visit(FactorConstNode node) {
		Code.load(node.obj);
	}

	public void visit(DesignatorNode node) {
		if(node.obj.getType() == TabExtension.enumType) return;
		SyntaxNode parent = node.getParent();
		if (!(parent instanceof DesignatorStmtAssignNode) && !(parent instanceof MethodCallDeclNode) && !(parent instanceof ReadStmtNode) && !(parent instanceof DesignatorStmtIncNode)&& !(parent instanceof DesignatorStmtDecNode)) {
			Code.load(node.obj);
		}
	}

	public void visit(DesignatorIndexNode node){
		SyntaxNode parent = node.getParent();
		if (!(parent instanceof DesignatorStmtAssignNode) && !(parent instanceof MethodCallDeclNode) && !(parent instanceof ReadStmtNode)&& !(parent instanceof DesignatorStmtIncNode)&& !(parent instanceof DesignatorStmtDecNode)) {
			Code.load(node.obj);
		}
	}

	public void visit(DesignatorChainNode node) {
		if(node.getDesignator().obj.getType() == TabExtension.enumType){
			Code.load(node.obj);
		}
	}

	public void visit(DesignatorStmtAssignNode node) {
		Code.store(node.getDesignator().obj);
	}

	public void visit(DesignatorStmtIncNode node) {
		if(node.getDesignator().obj.getKind() == Obj.Elem) Code.put(Code.dup2);
		Code.load(node.getDesignator().obj);
		Code.put(Code.const_1);
		Code.put(Code.add);
		Code.store(node.getDesignator().obj);
	}

	public void visit(DesignatorStmtDecNode node) {
		if(node.getDesignator().obj.getKind() == Obj.Elem) Code.put(Code.dup2);
		Code.load(node.getDesignator().obj);
		Code.put(Code.const_1);
		Code.put(Code.sub);
		Code.store(node.getDesignator().obj);
	}

	public void visit(ExprAddopNode node) {
		if(node.getAddop() instanceof AddopPlusNode){
			Code.put(Code.add);
		}
		else if(node.getAddop() instanceof AddopMinusNode){
			Code.put(Code.sub);
		}
	}
	public void visit(ExprNode node) {
		if(node.getOptMinus() instanceof UnMinus){
			Code.put(Code.neg);
		}
	}
	public void visit(TermMulopNode node) {
		if(node.getMulop() instanceof MulopMulNode){
			Code.put(Code.mul);
		}
		else if(node.getMulop() instanceof MulopDivNode){
			Code.put(Code.div);
		}
		else if(node.getMulop() instanceof MulopModNode){
			Code.put(Code.rem);
		}
	}

	public void visit(MethodCallNode node) {
		Obj functionObj = node.getMethodCallDecl().obj;
		if(functionObj.getName().equals("chr") || functionObj.getName().equals("ord")) return;
		if(functionObj.getName().equals("len")){
			Code.put(Code.arraylength);
			return;
		}
		int offset = functionObj.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(offset);
		if(node.getParent() instanceof DesignatorStmtCallNode && functionObj.getType() != Tab.noType){
			Code.put(Code.pop);
		}
	}

	public void visit(PrintStmtNode node) {
	    if(node.getPrintOptNumFields() instanceof PrintOptNumFieldsNode){
	        PrintOptNumFieldsNode printOptNumFieldsNode = (PrintOptNumFieldsNode) node.getPrintOptNumFields();
			Code.loadConst(printOptNumFieldsNode.getValue());
        }
		else Code.put(Code.const_1);
		if(node.getExpr().obj.getType() == Tab.intType || node.getExpr().obj.getType() == TabExtension.boolType){
			Code.put(Code.print);
		}
		else if (node.getExpr().obj.getType() == Tab.charType){
			Code.put(Code.bprint);
		}
	}

	public void visit(ReadStmtNode node){
		Struct type = node.getDesignator().obj.getType();
		if(type.getKind() == Struct.Array){
			type = type.getElemType();
		}
		if(type == Tab.charType){
			Code.put(Code.bread);
		}
		else{
			Code.put(Code.read);
		}
		Code.store(node.getDesignator().obj);
	}

    public void visit(FactorNewNode node){
        if(node.getOptNewArray() instanceof OptNewArrayNode){
            int b = 0;
            Code.put(Code.newarray);
            if(node.getType().obj.getType() == Tab.intType) b = 1;
            Code.put(b);
        }
        else{
            // KLASA...
        }
    }

    public void visit(CondFactRelopNode node){
		Relop op = node.getRelop();
		if(op instanceof RelopEqNode){
			Code.putFalseJump(Code.eq, Code.pc + 7);
		}
		if(op instanceof RelopNeqNode){
			Code.putFalseJump(Code.ne, Code.pc + 7);
		}
		if(op instanceof RelopGtNode){
			Code.putFalseJump(Code.gt, Code.pc + 7);
		}
		if(op instanceof RelopGteNode){
			Code.putFalseJump(Code.ge, Code.pc + 7);
		}
		if(op instanceof RelopLtNode){
			Code.putFalseJump(Code.lt, Code.pc + 7);
		}
		if(op instanceof RelopLteNode){
			Code.putFalseJump(Code.le, Code.pc + 7);
		}
		Code.loadConst(1);
		Code.putJump(Code.pc + 4);
		Code.loadConst(0);
	}

	public void visit(ConditionOrNode node){
		Code.loadConst(0);
		Code.putFalseJump(Code.ne, Code.pc + 8);

		//if(second != 0)
		Code.put(Code.pop);
		Code.loadConst(1);
		Code.putJump(Code.pc + 12);

		//else
		Code.loadConst(0);
		Code.putFalseJump(Code.ne, Code.pc + 7);

		//if(first != 0)
		Code.loadConst(1);
		Code.putJump(Code.pc + 4);

		//else
		Code.loadConst(0);
	}

	public void visit(CondTermAndNode node){
		Code.loadConst(1);
		Code.putFalseJump(Code.ne, Code.pc + 8);

		//if(second != 1)
		Code.put(Code.pop);
		Code.loadConst(0);
		Code.putJump(Code.pc + 12);

		//else
		Code.loadConst(1);
		Code.putFalseJump(Code.ne, Code.pc + 7);

		//if(first != 1)
		Code.loadConst(0);
		Code.putJump(Code.pc + 4);

		//else
		Code.loadConst(1);
	}

	Stack<ForStatementContext> forStatementContextStack = new Stack<>();
	static class ForStatementContext{
		public boolean hasCondition = false;
		public int optConditionFixup = 0;
		public int bodyFixup = 0;
		public int optDesignatorStmtAddr = 0;
		public int optConditionAddr = 0;
		public ArrayList<Integer> breakFixups = new ArrayList<>();
	}

	public void visit(ForDeclNode node){
		ForStatementContext forStatementContext = new ForStatementContext();
		forStatementContext.optConditionAddr = Code.pc;
		forStatementContextStack.push(forStatementContext);
	}

	public void visit(OptConditionNode node){
		ForStatementContext forStatementContext = forStatementContextStack.peek();
		forStatementContext.hasCondition = true;
		Code.loadConst(0);
		Code.putFalseJump(Code.ne, 0);
		forStatementContext.optConditionFixup = Code.pc - 2;
		Code.putJump(0);
		forStatementContext.bodyFixup = Code.pc - 2;
		forStatementContext.optDesignatorStmtAddr = Code.pc;
	}

	public void visit(NoOptConditionNode node){
		ForStatementContext forStatementContext = forStatementContextStack.peek();
		Code.putJump(0);
		forStatementContext.bodyFixup = Code.pc - 2;
		forStatementContext.optDesignatorStmtAddr = Code.pc;
	}

	public void visit(PostForOptDesignatorStatementNode node){
		ForStatementContext forStatementContext = forStatementContextStack.peek();
		Code.putJump(forStatementContext.optConditionAddr);
		Code.fixup(forStatementContext.bodyFixup);
	}

	public void visit(ForStmtNode node){
		ForStatementContext forStatementContext = forStatementContextStack.peek();
		Code.putJump(forStatementContext.optDesignatorStmtAddr);
		if(forStatementContext.hasCondition) Code.fixup(forStatementContextStack.peek().optConditionFixup);
		for(int i = 0; i < forStatementContext.breakFixups.size(); i++){
			Code.fixup(forStatementContext.breakFixups.get(i));
		}
		forStatementContextStack.pop();
	}

	public void visit(BreakStmtNode node){
		if(forStatementContextStack.empty()) return;
		ForStatementContext forStatementContext = forStatementContextStack.peek();
		Code.putJump(0);
		forStatementContext.breakFixups.add(Code.pc - 2);
	}

	public void visit(ContinueStmtNode node){
		if(forStatementContextStack.empty()) return;
		ForStatementContext forStatementContext = forStatementContextStack.peek();
		Code.putJump(forStatementContext.optDesignatorStmtAddr);
	}

	Stack<IfElseStatementContext> ifElseStatementContextStack = new Stack<>();
	static class IfElseStatementContext{
		public int jmpAddr = 0;
	}

	public void visit(IfDeclNode node){
		Code.loadConst(0);
		Code.putFalseJump(Code.ne, 0);
		IfElseStatementContext ifElseStatementContext = new IfElseStatementContext();
		ifElseStatementContext.jmpAddr = Code.pc - 2;
		ifElseStatementContextStack.push(ifElseStatementContext);
	}

	public void visit(ElseDeclNode node)
	{
		Code.putJump(0);
		int fixupAddr = ifElseStatementContextStack.pop().jmpAddr;
		Code.fixup(fixupAddr);
		IfElseStatementContext ifElseStatementContext = new IfElseStatementContext();
		ifElseStatementContext.jmpAddr = Code.pc - 2;
		ifElseStatementContextStack.push(ifElseStatementContext);
	}
	public void visit(IfStmtNode node) {
		int fixupAddr = ifElseStatementContextStack.pop().jmpAddr;
		Code.fixup(fixupAddr);
	}
	public void visit(IfElseStmtNode node) {
		int fixupAddr = ifElseStatementContextStack.pop().jmpAddr;
		Code.fixup(fixupAddr);
	}
}
