/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.dom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jface.text.IDocument;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import org.eclipse.jdt.internal.core.dom.rewrite.ListRewriteEvent;
import org.eclipse.jdt.internal.core.dom.rewrite.NodeRewriteEvent;
import org.eclipse.jdt.internal.core.dom.rewrite.RewriteEvent;
import org.eclipse.jdt.internal.core.dom.rewrite.RewriteEventStore;
import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBuffer;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Original version of the ASTRewrite: Mix of modifying an decribing API.
 * Will be removed soon, please adapt to ASTRewrite.
 */
public final class OldASTRewrite extends ASTRewrite { // illegal subclassing
		
	private HashMap fChangedProperties;

	private boolean fHasASTModifications;
	private ASTNode fRootNode;
	
	/**
	 * Creates the <code>OldASTRewrite</code> object.
	 * @param node A node which is parent to all modified, changed or tracked nodes.
	 */
	public OldASTRewrite(ASTNode node) {
		super(node.getAST());
		fRootNode= node;
		fChangedProperties= new HashMap();

		fHasASTModifications= false;
		
		// override the parent to child mapper to correct back modified modes from inserts
		getRewriteEventStore().setNodePropertyMapper(new RewriteEventStore.INodePropertyMapper() {
			public Object getOriginalValue(ASTNode parent, StructuralPropertyDescriptor childProperty) {
				Object originalValue= parent.getStructuralProperty(childProperty);
				if (parent.getStartPosition() == -1) {
					return originalValue; // ignore unnecessary inserts
				}
				if (originalValue instanceof List) {
					List originalList= (List) originalValue;
					ArrayList fixedList= new ArrayList(originalList.size());
					for (int i= 0; i < originalList.size(); i++) {
						ASTNode curr= (ASTNode) originalList.get(i);
						if (!isInserted(curr)) {
							fixedList.add(curr);
						}
					}
					return fixedList;
				} else if (originalValue instanceof ASTNode) {
					if (isInserted((ASTNode) originalValue)) {
						return null;
					}
				}
				return originalValue;
			}
		});
	}
		
	/**
	 * Perform rewriting: Analyses AST modifications and creates text edits that describe changes to the
	 * underlying code. Edits do only change code when the corresponding node has changed. New code
	 * is formatted using the standard code formatter.
	 * @param textBuffer Text buffer which is describing the code of the AST passed in in the
	 * constructor. This buffer is accessed read-only.
	 * @param rootEdit
	 */
	public final void rewriteNode(TextBuffer textBuffer, TextEdit rootEdit) {
		try {
			TextEdit res= rewriteAST(textBuffer.getDocument(), null);
			rootEdit.addChildren(res.removeChildren());
		} catch (IllegalArgumentException e) {
			JavaPlugin.log(e);
		}
	}
	
	/**
	 * New API.
	 */
	public TextEdit rewriteAST(IDocument document, Map options) {
		convertOldToNewEvents();
		return super.rewriteAST(document, options);
	}
	
	/**
	 * Returns the root node of the rewrite. All modifications or move/copy sources lie
	 * inside the root.
	 * @return Returns the root node or <code>null</code> if no node has been
	 * changed.
	 */
	public ASTNode getRootNode() {
		return fRootNode;
	}
	
	/**
	 * Convert the old to the new events. Can only be done when rewrite is started
	 * (inserted node must not yet be added to the AST when marked)
	 */
	private void convertOldToNewEvents() {
		Set processedListEvents= new HashSet();
		
		for (Iterator iter= fChangedProperties.keySet().iterator(); iter.hasNext(); ) {
			ASTNode node= (ASTNode) iter.next();
			ASTInsert object= getChangeProperty(node);
			if (object != null) {
				if (node.getParent().getStartPosition() != -1) { // ignore unnecessary inserts
					processChange(node, null, node, object.description, processedListEvents);
					if (object.isBoundToPrevious) {
						getRewriteEventStore().setInsertBoundToPrevious(node);
					}
				}
			}
		}
	}
	
	private void processChange(ASTNode nodeInAST, ASTNode originalNode, ASTNode newNode, TextEditGroup desc, Set processedListEvents) {
		ASTNode parent= nodeInAST.getParent();
		StructuralPropertyDescriptor childProperty= nodeInAST.getLocationInParent();
		if (childProperty.isChildListProperty()) {
			ListRewriteEvent event= getRewriteEventStore().getListEvent(parent, childProperty, true); // create
			if (processedListEvents.add(event)) {
				convertListChange(event, (List) parent.getStructuralProperty(childProperty));
			}
		} else {
			NodeRewriteEvent event= getRewriteEventStore().getNodeEvent(parent, childProperty, true);
			event.setNewValue(newNode);
			getRewriteEventStore().setEventEditGroup(event, desc);
		}		
	}
	
	
	private void convertListChange(ListRewriteEvent listEvent, List modifiedList) {
		for (int i= 0; i < modifiedList.size(); i++) {
			ASTNode curr= (ASTNode) modifiedList.get(i);
			ASTInsert object= getChangeProperty(curr);
			if (object != null) {
				RewriteEvent event= listEvent.insert(curr, i);
				getRewriteEventStore().setEventEditGroup(event, object.description);
				if (object.isBoundToPrevious) {
					getRewriteEventStore().setInsertBoundToPrevious(curr);
				}
			}
		}
	}
	
	private boolean isInsertBoundToPreviousByDefault(ASTNode node) {
		return (node instanceof Statement || node instanceof FieldDeclaration);
	}
	
	/**
	 * Removes all modifications applied to the given AST.
	 */
	public final void removeModifications() {
		if (fHasASTModifications) {
			getRootNode().accept(new ASTRewriteClear(this));
			fHasASTModifications= false;
		}
		fChangedProperties.clear();

		clearRewrite();
	}
	
	public boolean hasASTModifications() {
		return fHasASTModifications;
	}
	
	/**
	 * Clears all events and other internal structures.
	 */
	protected final void clearRewrite() {
		getRewriteEventStore().clear();
		getNodeStore().clear();
	}
	
	public final boolean isCollapsed(ASTNode node) {
		return getNodeStore().isCollapsed(node);
	}
	
			
	/**
	 * Marks a node as inserted. The node must not exist. To insert an existing node (move or copy),
	 * create a copy target first and insert this target node. ({@link #createCopyTarget(ASTNode)})
	 * @param node The node to be marked as inserted.
	 * @param description Description of the change.
	 */
	public final void markAsInserted(ASTNode node, TextEditGroup description) {
		Assert.isTrue(!isCollapsed(node), "Tries to insert a collapsed node"); //$NON-NLS-1$
		ASTInsert insert= new ASTInsert();
		insert.isBoundToPrevious= isInsertBoundToPreviousByDefault(node);
		insert.description= description;
		setChangeProperty(node, insert);
		fHasASTModifications= true;
		node.setSourceRange(-1, 0); // avoid troubles later when annotating extra node ranges.
	}

	/**
	 * Marks a node as inserted. The node must not exist. To insert an existing node (move or copy),
	 * create a copy target first and insert this target node. ({@link #createCopyTarget(ASTNode)})
	 * @param node The node to be marked as inserted.
	 */
	public final void markAsInserted(ASTNode node) {
		markAsInserted(node, (TextEditGroup) null);
	}
	
	
	/**
	 * Create a placeholder for a sequence of new statements to be inserted or placed at a single place.
	 * @param children The target nodes to collapse
	 * @return A placeholder node that stands for all of the statements 
	 */
	public final Block getCollapseTargetPlaceholder(Statement[] children) {
		Block res= getNodeStore().createCollapsePlaceholder();
		List statements= res.statements();
		for (int i= 0; i < children.length; i++) {
			statements.add(children[i]);
		}
		return res;
	}
	
	/**
	 * Track a node, old API
	 * @param node
	 * @param editGroup
	 */
	public final void markAsTracked(ASTNode node, TextEditGroup editGroup) {
		if (getRewriteEventStore().getTrackedNodeData(node) != null) {
			throw new IllegalArgumentException("Node is already marked as tracked"); //$NON-NLS-1$
		}
		
		getRewriteEventStore().setTrackedNodeData(node, editGroup);
	}	
		
	/**
	 * Succeeding nodes in a list are collapsed and represented by a new 'compound' node. The new compound node is inserted in the list
	 * and replaces the collapsed node. The compound node can be used for rewriting, e.g. a copy can be created to move
	 * a whole range of statements. This operation modifies the AST.
	 * @param list
	 * @param index
	 * @param length
	 * @return
	 */	
	public final ASTNode collapseNodes(List list, int index, int length) {
		Assert.isTrue(index >= 0 && length > 0 && list.size() >= (index + length), "Index or length out of bound"); //$NON-NLS-1$
		
		ASTNode firstNode= (ASTNode) list.get(index);
		ASTNode lastNode= (ASTNode) list.get(index + length - 1);
		validateIsInsideAST(firstNode);
		validateIsInsideAST(lastNode);
		
		Assert.isTrue(lastNode instanceof Statement, "Can only collapse statements"); //$NON-NLS-1$
		
		int startPos= firstNode.getStartPosition();
		int endPos= lastNode.getStartPosition() + lastNode.getLength();
				
		Block compoundNode= getNodeStore().createCollapsePlaceholder();
		List children= compoundNode.statements();
		compoundNode.setSourceRange(startPos, endPos - startPos);
		
		StructuralPropertyDescriptor childProperty= firstNode.getLocationInParent();
		
		ListRewriteEvent existingEvent= getRewriteEventStore().getListEvent(firstNode.getParent(), childProperty, false);
		if (existingEvent != null) {
			RewriteEvent[] origChildren= existingEvent.getChildren();
			Assert.isTrue(origChildren.length == list.size());
			RewriteEvent[] newChildren= new RewriteEvent[origChildren.length - length + 1];
			System.arraycopy(origChildren, 0, newChildren, 0, index);
			newChildren[index]= new NodeRewriteEvent(compoundNode, compoundNode);
			System.arraycopy(origChildren, index + length, newChildren, index + 1, origChildren.length - index - length);
			getRewriteEventStore().addEvent(firstNode.getParent(), childProperty, new ListRewriteEvent(newChildren)); // replace
			
			RewriteEvent[] newCollapsedChildren= new RewriteEvent[length];
			System.arraycopy(origChildren, index, newCollapsedChildren, 0, length);
			getRewriteEventStore().addEvent(compoundNode, Block.STATEMENTS_PROPERTY, new ListRewriteEvent(newCollapsedChildren));
		}
		
		for (int i= 0; i < length; i++) {
			Object curr= list.remove(index);
			children.add(curr);
		}
		list.add(index, compoundNode);
		
		fHasASTModifications= true;
		
		return compoundNode;
	}
	
	private final void validateIsInsideAST(ASTNode node) {
		if (node.getStartPosition() == -1) {
			throw new IllegalArgumentException("Node is not an existing node"); //$NON-NLS-1$
		}
	
		if (node.getAST() != getAST()) {
			throw new IllegalArgumentException("Node is not inside the AST"); //$NON-NLS-1$
		}
	}
		
	public final boolean isInserted(ASTNode node) {
		return getChangeProperty(node) != null;
	}
	
	
	public boolean isRemoved(ASTNode node) {
		return getRewriteEventStore().getChangeKind(node) == RewriteEvent.REMOVED;
	}	
	
	public boolean isReplaced(ASTNode node) {
		return getRewriteEventStore().getChangeKind(node) == RewriteEvent.REPLACED;
	}
		
	
	public final ASTNode getReplacingNode(ASTNode node) {
		RewriteEvent event= getRewriteEventStore().findEvent(node, RewriteEventStore.ORIGINAL);
		if (event != null && event.getChangeKind() == RewriteEvent.REPLACED) {
			return (ASTNode) event.getNewValue();
		}
		return null;
	}
						
	private final void setChangeProperty(ASTNode node, ASTInsert change) {
		fChangedProperties.put(node, change);
	}
	
	private final ASTInsert getChangeProperty(ASTNode node) {
		return (ASTInsert) fChangedProperties.get(node);
	}
		
	private static class ASTInsert {
		public TextEditGroup description;
		public boolean isBoundToPrevious;	
	}

}
