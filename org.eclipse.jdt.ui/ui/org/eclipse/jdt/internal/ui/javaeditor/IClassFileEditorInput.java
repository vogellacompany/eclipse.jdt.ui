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
package org.eclipse.jdt.internal.ui.javaeditor;



import org.eclipse.jdt.core.IClassFile;
import org.eclipse.ui.IEditorInput;


/**
 * Editor input for class files.
 */
public interface IClassFileEditorInput extends IEditorInput {
	
	/** 
	 * Returns the class file acting as input.
	 */
	public IClassFile getClassFile();	
}

