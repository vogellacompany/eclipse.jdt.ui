package org.eclipse.jdt.internal.corext.dom;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;

import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.refactoring.changes.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.textmanipulation.SimpleTextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBuffer;
import org.eclipse.jdt.internal.corext.textmanipulation.TextRegion;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.Strings;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.correction.ASTResolving;

/**
  */
public class ASTRewriteAnalyzer extends ASTVisitor {

	private static final String KEY= "ASTChangeData";
	
	private static final int INSERT= 1;
	private static final int REMOVE= 2;
	private static final int MODIFY= 3;


	public static void markAsInserted(ASTNode node) {
		node.setProperty(KEY, new ASTRewriteInfo(INSERT, null));
	}
	
	public static void markAsRemoved(ASTNode node) {
		node.setProperty(KEY, new ASTRewriteInfo(REMOVE, null));
	}
	
	public static void markAsModified(ASTNode node, ASTNode modifiedNode) {
		node.setProperty(KEY, new ASTRewriteInfo(MODIFY, modifiedNode));
	}
	
	public static ASTRewriteInfo getChangeData(ASTNode node) {
		return (ASTRewriteInfo) node.getProperty(KEY);
	}
	
	public static class ASTRewriteInfo {
		
	
		private int fOperation;
		private ASTNode fModifiedNode;
		
		public ASTRewriteInfo(int op, ASTNode modifiedNode) {
			fOperation= op;
			fModifiedNode= modifiedNode;
		}
		
		public boolean isInsert() {
			return fOperation == INSERT;
		}
			
		public boolean isRemove() {
			return fOperation == REMOVE;
		}	
		
		public boolean isModifiy() {
			return fOperation == MODIFY;
		}
		
		/**
		 * Returns the modified node.
		 * @return ASTNode
		 */
		public ASTNode getModifiedNode() {
			return fModifiedNode;
		}
	
	}	
	


	private CompilationUnitChange fChange;
	private TextBuffer fTextBuffer;

	/**
	 * Constructor for ASTChangeAnalyzer.
	 */
	public ASTRewriteAnalyzer(TextBuffer textBuffer, CompilationUnitChange change) {
		fTextBuffer= textBuffer;
		fChange= change;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(ReturnStatement)
	 */
	public boolean visit(ReturnStatement node) {
		ASTRewriteInfo changeData= getChangeData(node);
		if (changeData != null && changeData.isModifiy()) {
			ReturnStatement modified= (ReturnStatement) changeData.getModifiedNode();
			if (modified.getExpression() != node.getExpression()) {
				if (node.getExpression() != null) {
					modifyExpression(node.getExpression(), modified.getExpression());
				} else {
					insertExpression(modified.getExpression(), node.getStartPosition(), ITerminalSymbols.TokenNamereturn);
				}
					
			}
		}
		Expression expression= node.getExpression();
		if (expression != null) {
			changeData= getChangeData(expression);
			if (changeData != null && !changeData.isModifiy()) {
				if (changeData.isRemove()) {
					modifyExpression(expression, null);
				} else {
					insertExpression(expression, node.getStartPosition(), ITerminalSymbols.TokenNamereturn);
				}
			}
		}
		return false;
	}	
	
	private void insertExpression(Expression inserted, int offset, int prevToken) {
		try {
			IScanner scanner= ASTResolving.createScanner(fChange.getCompilationUnit(), offset);
			while (scanner.getNextToken() != prevToken) {
			}
			
			int pos= scanner.getCurrentTokenEndPosition() + 1;
			
			String str= " " + generateSource(inserted, 0);
			fChange.addTextEdit("Insert Expression", SimpleTextEdit.createInsert(pos, str));
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
		} catch (InvalidInputException e) {
			JavaPlugin.log(e);
		}
	}
	
	
	private void modifyExpression(Expression old, Expression modified) {
		if (modified == null) {
			int startPos= old.getStartPosition();
			int endPos= startPos + old.getLength();
			TextRegion lineStart= fTextBuffer.getLineInformationOfOffset(startPos);
			while (startPos > lineStart.getOffset() && Character.isWhitespace(fTextBuffer.getChar(startPos - 1))) {
				startPos--;
			}
			fChange.addTextEdit("Remove Expression", SimpleTextEdit.createReplace(startPos, endPos - startPos, ""));
		} else {
			String str= generateSource(modified, 0);
			fChange.addTextEdit("Modify Expression", SimpleTextEdit.createReplace(old.getStartPosition(), old.getLength(), str));
		}
	}
	

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(Block)
	 */
	public boolean visit(Block block) {
		
		// insert / remove
		List list= block.statements();
		Statement last= null;
		for (int i= 0; i < list.size(); i++) {
			Statement elem= (Statement) list.get(i);
			ASTRewriteInfo changeData= getChangeData(elem);
			if (changeData != null && !changeData.isModifiy()) {
				if (changeData.isRemove()) {
					removeStatement(elem);
				} else {
					insertStatement(block, elem, last);
					last= elem;
				}
			} else {
				elem.accept(this);
				last= elem;
			}
			
		}		
		return false;
	}
	
	private void removeStatement(Statement elem) {
		int start= elem.getStartPosition();
		int end= start + elem.getLength();
		
		TextRegion endRegion= fTextBuffer.getLineInformationOfOffset(end);
		int lineEnd= endRegion.getOffset() + endRegion.getLength();
		// move end to include all spaces and tabs
		while (end < lineEnd && Character.isWhitespace(fTextBuffer.getChar(end))) {
			end++;
		}
		if (lineEnd == end) { // if there is no comment / other statement remove the line (indent + new line)
			int startLine= fTextBuffer.getLineOfOffset(start);
			if (startLine > 0) {
				TextRegion prevRegion= fTextBuffer.getLineInformation(startLine - 1);
				int cutPos= prevRegion.getOffset() + prevRegion.getLength();
				String str= fTextBuffer.getContent(cutPos, start - cutPos);
				if (Strings.containsOnlyWhitespaces(str)) {
					start= cutPos;
				}
			}
		}
		
		fChange.addTextEdit("Remove Statement", SimpleTextEdit.createReplace(start, end - start, ""));
	}

	private void insertStatement(Block block, Statement elem, Statement sibiling) {
		int insertPos;
		int indent;
		if (sibiling == null) {
			indent= getIndent(block) + 1;
			insertPos= block.getStartPosition() + 1;
		} else {
			indent= getIndent(sibiling);
			insertPos= sibiling.getStartPosition() + sibiling.getLength();
		}
			
		StringBuffer buf= new StringBuffer();
		buf.append(fTextBuffer.getLineDelimiter());
		buf.append(generateSource(elem, indent));
		
		fChange.addTextEdit("Add Statement", SimpleTextEdit.createInsert(insertPos, buf.toString()));
	}

	private int getIndent(ASTNode block) {
		int line= fTextBuffer.getLineOfOffset(block.getStartPosition());
		return fTextBuffer.getLineIndent(line, CodeFormatterUtil.getTabWidth());
	}

	
	private String generateSource(ASTNode node, int indent) {
		String str= ASTNodes.asString(node);
		return StubUtility.codeFormat(str, indent, fTextBuffer.getLineDelimiter());
	}
	


}
