package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import java_cup.runtime.Symbol;
import rs.ac.bg.etf.pp1.ast.SyntaxNode;
import rs.ac.bg.etf.pp1.util.Log4JUtils;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;

public class Compiler {
	static {
		DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
		//Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
	}
	public static class CompilerHelper {
		public File sourceFile;
		public File objFile;
		public boolean outputTable = false;
		public boolean outputTree = false;
		public CompilerHelper(String[] args, Logger log){
			if (args.length < 2) {
				log.error("Not enough arguments supplied! Usage: MJCompiler <source-file> <obj-file> [/dumptable] [/dumptree]");
				return;
			}
			sourceFile = new File(args[0]);
			if (!sourceFile.exists()) {
				log.error("Source file [" + sourceFile.getAbsolutePath() + "] not found!");
				sourceFile = null;
			}
			if(args.length > 1) objFile = new File(args[1]);
			for(int i = 2; i < args.length; i++){
				if(args[i].toLowerCase().equals("/dumptable")){
					outputTable = true;
				}
				if(args[i].toLowerCase().equals("/dumptree")){
					outputTree = true;
				}
			}
		}
	}
	public static void main(String[] args) throws Exception {
		Logger log = Logger.getLogger(Compiler.class);

		CompilerHelper helper = new CompilerHelper(args, log);

		if(helper.sourceFile == null) return;

		log.info("Compiling source file: " + helper.sourceFile.getAbsolutePath());
		
		try (BufferedReader br = new BufferedReader(new FileReader(helper.sourceFile))) {
			Yylex lexer = new Yylex(br);
			MJParser parser = new MJParser(lexer);

	        Symbol s = parser.parse();
	        SyntaxNode prog = (SyntaxNode)(s.value);

	        if(helper.outputTree) System.out.print(prog.toString());
	        
			Tab.init();
			TabExtension.init();

			SemanticAnalyzer semanticCheck = new SemanticAnalyzer();
			prog.traverseBottomUp(semanticCheck);

	        if(helper.outputTable) Tab.dump();
	        
	        if (!parser.errorDetected && semanticCheck.passed()) {
	        	log.info("Generating bytecode file: " + helper.objFile.getAbsolutePath());
	        	if (helper.objFile.exists()) helper.objFile.delete();

	        	CodeGenerator codeGenerator = new CodeGenerator(semanticCheck.staticVarCount);
	        	prog.traverseBottomUp(codeGenerator);

	        	Code.dataSize = codeGenerator.vtpCurrAdr;
	        	Code.mainPc = codeGenerator.getMainPc();

	        	Code.write(new FileOutputStream(helper.objFile));
	        	log.info("Compiling successfully done");
	        }
	        else {
	        	log.error("Compiling was unsuccessful");
	        }
		}
	}
}
