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
package org.eclipse.jdt.internal.corext.refactoring.typeconstraints;

import org.eclipse.jdt.internal.corext.Assert;

public final class SimpleTypeConstraint implements ITypeConstraint {
	
	private final ConstraintVariable fLeft;
	private final ConstraintVariable fRight;
	private final ConstraintOperator fOperator;
	
	/* package */ SimpleTypeConstraint(ConstraintVariable left, ConstraintVariable right, ConstraintOperator operator) {
		Assert.isNotNull(left);
		Assert.isNotNull(right);
		Assert.isNotNull(operator);
		fLeft= left;
		fRight= right;
		fOperator= operator;
	}
	
	public  ConstraintVariable getLeft() {
		return fLeft;
	}

	public  ConstraintVariable getRight() {
		return fRight;
	}

	public ConstraintOperator getOperator() {
		return fOperator;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public  String toString(){
		return getLeft().toString() + " " + fOperator.toString() + " " + getRight().toString(); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.experiments.TypeConstraint#toResolvedString()
	 */
	public  String toResolvedString() {
		return getLeft().toResolvedString() + " " + fOperator.toString() + " " + getRight().toResolvedString(); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.experiments.ITypeConstraint#isSimpleTypeConstraint()
	 */
	public  boolean isSimpleTypeConstraint() {
		return true;
	}
	
	public boolean isSubtypeConstraint(){
		return fOperator.isSubtypeOperator();
	}

	public boolean isStrictSubtypeConstraint(){
		return fOperator.isStrictSubtypeOperator();
	}

	public boolean isEqualsConstraint(){
		return fOperator.isEqualsOperator();
	}

	public boolean isDefinesConstraint(){
		return fOperator.isDefinesOperator();
	}	
}
