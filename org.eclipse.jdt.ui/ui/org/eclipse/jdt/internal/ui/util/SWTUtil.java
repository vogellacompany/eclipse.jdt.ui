/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.util;


import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Caret;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

import org.eclipse.jface.dialogs.IDialogConstants;

/**
 * Utility class to simplify access to some SWT resources. 
 */
public class SWTUtil {
	
	/**
	 * Returns the standard display to be used. The method first checks, if
	 * the thread calling this method has an associated disaply. If so, this
	 * display is returned. Otherwise the method returns the default display.
	 */
	public static Display getStandardDisplay() {
		Display display;
		display= Display.getCurrent();
		if (display == null)
			display= Display.getDefault();
		return display;		
	}
	
	/**
	 * Returns the shell for the given widget. If the widget doesn't represent
	 * a SWT object that manage a shell, <code>null</code> is returned.
	 * 
	 * @return the shell for the given widget
	 */
	public static Shell getShell(Widget widget) {
		if (widget instanceof Control)
			return ((Control)widget).getShell();
		if (widget instanceof Caret)
			return ((Caret)widget).getParent().getShell();
		if (widget instanceof DragSource)
			return ((DragSource)widget).getControl().getShell();
		if (widget instanceof DropTarget)
			return ((DropTarget)widget).getControl().getShell();
		if (widget instanceof Menu)
			return ((Menu)widget).getParent().getShell();
		if (widget instanceof ScrollBar)
			return ((ScrollBar)widget).getParent().getShell();
							
		return null;	
	}
	
	private static double fgHorizontalDialogUnitSize= 0.0;
	private static double fgVerticalDialogUnitSize= 0.0;
	
	private static void initializeDialogUnits(Control control) {
		GC gc= new GC(control);
		gc.setFont(control.getFont());
		int averageWidth= gc.getFontMetrics().getAverageCharWidth();
		int height = gc.getFontMetrics().getHeight();
		gc.dispose();
	
		fgHorizontalDialogUnitSize = averageWidth * 0.25;
		fgVerticalDialogUnitSize = height * 0.125;
	}
	
	/**
	 * @see DialogPage#convertHeightInCharsToPixels
	 */
	private static int convertHeightInCharsToPixels(int chars) {
		return convertVerticalDLUsToPixels(chars * 8);
	}

	/**
	 * @see DialogPage#convertHorizontalDLUsToPixels
	 */
	private static int convertHorizontalDLUsToPixels(int dlus) {
		return (int)Math.round(dlus * fgHorizontalDialogUnitSize);
	}

	/**
	 * @see DialogPage#convertVerticalDLUsToPixels
	 */
	private static int convertVerticalDLUsToPixels(int dlus) {
		return (int)Math.round(dlus * fgVerticalDialogUnitSize);
	}
	
	/**
	 * @see DialogPage#convertWidthInCharsToPixels
	 */
	private static int convertWidthInCharsToPixels(int chars) {
		return convertHorizontalDLUsToPixels(chars * 4);
	}
	
	/**
	 * Returns a width hint for a button control.
	 */
	public static int getButtonWidthHint(Button button) {
		if (fgHorizontalDialogUnitSize == 0.0) {
			initializeDialogUnits(button);
		}
		int widthHint= convertHorizontalDLUsToPixels(IDialogConstants.BUTTON_WIDTH);
		return Math.max(widthHint, button.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x);
	}

	/**
	 * Returns a height hint for a button control.
	 */		
	public static int getButtonHeigthHint(Button button) {
		if (fgHorizontalDialogUnitSize == 0.0) {
			initializeDialogUnits(button);
		}
		return convertVerticalDLUsToPixels(IDialogConstants.BUTTON_HEIGHT);
	}		
	
		 
}