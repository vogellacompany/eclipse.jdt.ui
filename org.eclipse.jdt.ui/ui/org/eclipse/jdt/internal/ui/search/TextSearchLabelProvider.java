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
package org.eclipse.jdt.internal.ui.search;

import java.text.MessageFormat;

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.search.ui.text.AbstractTextSearchViewPage;

public abstract class TextSearchLabelProvider extends LabelProvider {

	private AbstractTextSearchViewPage fPage;
	private String fMatchCountFormat;

	public TextSearchLabelProvider(AbstractTextSearchViewPage page) {
		fPage= page;
		fMatchCountFormat= SearchMessages.getString("TextSearchLabelProvider.matchCountFormat"); //$NON-NLS-1$
	}
	
	public final String getText(Object element) {
		int matchCount= fPage.getInput().getMatchCount(element);
		String text= doGetText(element);
		if (matchCount < 2)
			return text;
		else {
			return MessageFormat.format(fMatchCountFormat, new Object[] { text, new Integer(matchCount) });
		}
	}

	protected abstract String doGetText(Object element);
}
