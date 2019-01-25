package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;

public class CounterVisitor extends VisitorAdaptor {
	
	protected int count;
	
	public int getCount() {
		return count;
	}
	
	public static class FormParamCounter extends CounterVisitor {

		@Override
		public void visit(FormParDeclNode node) {
			count++;
		}		
	}
	
	public static class VarCounter extends CounterVisitor {		
		@Override
		public void visit(VarDeclNode VarDecl) {
			count++;
		}
	}

	public static class ActParamCounter extends CounterVisitor {

		@Override
		public void visit(ActParNode node) {
			count++;
		}
		@Override
		public void visit(ActParsListNode node) {
			count++;
		}
	}
}
