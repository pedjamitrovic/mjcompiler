package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.CounterVisitor.FormParamCounter;
import rs.ac.bg.etf.pp1.CounterVisitor.VarCounter;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;

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
		SyntaxNode parent = node.getParent();
		if (!(parent instanceof DesignatorStmtAssignNode) && !(parent instanceof MethodCallNode)) {
			Code.load(node.obj);
		}
	}

	public void visit(DesignatorStmtAssignNode node) {
		Code.store(node.getDesignator().obj);
	}

	public void visit(ExprAddopNode node) {
		Code.put(Code.add);
	}

	public void visit(TermMulopNode node) {
		Code.put(Code.mul);
	}

	public void visit(MethodCallNode node) {
		Obj functionObj = node.getDesignator().obj;
		int offset = functionObj.getAdr() - Code.pc;
		Code.put(Code.call);
		Code.put2(offset);
		if(node.getParent() instanceof DesignatorStmtCallNode){
			Code.put(Code.pop);
		}
	}

	public void visit(PrintStmtNode node) {
		Code.put(Code.const_5);
		Code.put(Code.print);
	}
}
