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
package org.eclipse.jdt.internal.ui.text;


import org.eclipse.jface.text.rules.IWhitespaceDetector;

/**
 * A java aware white space detector.
 */
public class JavaWhitespaceDetector implements IWhitespaceDetector {

	/**
	 * @see IWhitespaceDetector#isWhitespace
	 */
	public boolean isWhitespace(char c) {
		return Character.isWhitespace(c);
	}
}
