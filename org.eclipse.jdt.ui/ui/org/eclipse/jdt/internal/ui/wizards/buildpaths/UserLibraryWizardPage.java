/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.jface.dialogs.Dialog;

import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.wizards.IClasspathContainerPage;
import org.eclipse.jdt.ui.wizards.IClasspathContainerPageExtension;
import org.eclipse.jdt.ui.wizards.IClasspathContainerPageExtension2;
import org.eclipse.jdt.ui.wizards.NewElementWizardPage;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.preferences.PreferencePageSupport;
import org.eclipse.jdt.internal.ui.preferences.UserLibraryPreferencePage;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.CheckedListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;

/**
 *
 */
public class UserLibraryWizardPage extends NewElementWizardPage implements IClasspathContainerPage, IClasspathContainerPageExtension, IClasspathContainerPageExtension2  {
	
	private CheckedListDialogField fLibrarySelector;
	private CPUserLibraryElement fEditResult;
	private Set fUsedPaths;
	private boolean fIsEditMode;
	private IJavaProject fProject;
	
	public UserLibraryWizardPage() {
		super("UserLibraryWizardPage"); //$NON-NLS-1$
		setTitle(NewWizardMessages.getString("UserLibraryWizardPage.title")); //$NON-NLS-1$
		setImageDescriptor(JavaPluginImages.DESC_WIZBAN_ADD_LIBRARY);
		updateDescription(null);
		fUsedPaths= new HashSet();
		fProject= UserLibraryPreferencePage.getPlaceholderProject();
		
		LibraryListAdapter adapter= new LibraryListAdapter();
		String[] buttonLabels= new String[] {
				NewWizardMessages.getString("UserLibraryWizardPage.list.config.button") //$NON-NLS-1$
		};
		fLibrarySelector= new CheckedListDialogField(adapter, buttonLabels, new CPListLabelProvider());
		fLibrarySelector.setDialogFieldListener(adapter);
		fLibrarySelector.setLabelText(NewWizardMessages.getString("UserLibraryWizardPage.list.label")); //$NON-NLS-1$
		fEditResult= null;
		updateStatus(validateSetting(Collections.EMPTY_LIST));
	}

	private void updateDescription(IClasspathEntry containerEntry) {
		if (containerEntry == null || containerEntry.getPath().segmentCount() != 2) {
			setDescription(NewWizardMessages.getString("UserLibraryWizardPage.description.new")); //$NON-NLS-1$
		} else {
			setDescription(NewWizardMessages.getFormattedString("UserLibraryWizardPage.description.edit", containerEntry.getPath().segment(1))); //$NON-NLS-1$
		}
	}
	
	private List updateLibraryList() {
		HashSet oldNames= new HashSet();
		HashSet oldCheckedNames= new HashSet();
		List oldElements= fLibrarySelector.getElements();
		for (int i= 0; i < oldElements.size(); i++) {
			CPUserLibraryElement curr= (CPUserLibraryElement) oldElements.get(i);
			oldNames.add(curr.getName());
			if (fLibrarySelector.isChecked(curr)) {
				oldCheckedNames.add(curr.getName());
			}
		}

		ArrayList entriesToCheck= new ArrayList();
		
		String[] names= JavaCore.getUserLibraryNames();
		Arrays.sort(names, Collator.getInstance());

		ArrayList elements= new ArrayList(names.length);
		for (int i= 0; i < names.length; i++) {
			String curr= names[i];
			IPath path= new Path(JavaCore.USER_LIBRARY_CONTAINER_ID).append(curr);
			try {
				IClasspathContainer container= JavaCore.getClasspathContainer(path, fProject);
				CPUserLibraryElement elem= new CPUserLibraryElement(curr, container, fProject);
				elements.add(elem);
				if (!oldCheckedNames.isEmpty()) {
					if (oldCheckedNames.contains(curr)) {
						entriesToCheck.add(elem);
					}
				} else {
					if (!oldNames.contains(curr)) {
						entriesToCheck.add(elem);
					}
				}
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
				// ignore
			}
		}
		fLibrarySelector.setElements(elements);
		return entriesToCheck;
	}
		
	private void doDialogFieldChanged(DialogField field) {
		if (field == fLibrarySelector) {
			List list= fLibrarySelector.getCheckedElements();
			if (fIsEditMode) {
				if (list.size() > 1) {
					if (fEditResult != null && list.remove(fEditResult)) {
						fLibrarySelector.setCheckedWithoutUpdate(fEditResult, false);
					}
					fEditResult= (CPUserLibraryElement) list.get(0); // take the first
					for (int i= 1; i < list.size(); i++) { // uncheck the rest
						fLibrarySelector.setCheckedWithoutUpdate(list.get(i), false);
					}
				} else if (list.size() == 1) {
					fEditResult= (CPUserLibraryElement) list.get(0);
				} else {
					if (fEditResult != null) {
						fLibrarySelector.setCheckedWithoutUpdate(fEditResult, true);
					} else {
						fEditResult= null;
					}
				}
			}
			updateStatus(validateSetting(list));
		}
	}
	
	private IStatus validateSetting(List selected) {
		int nSelected= selected.size();
		if (nSelected == 0) {
			return new StatusInfo(IStatus.ERROR, NewWizardMessages.getString("UserLibraryWizardPage.error.selectentry")); //$NON-NLS-1$
		} else if (fIsEditMode && nSelected > 1) {
			return new StatusInfo(IStatus.ERROR, NewWizardMessages.getString("UserLibraryWizardPage.error.selectonlyone")); //$NON-NLS-1$
		}
		for (int i= 0; i < selected.size(); i++) {
			CPUserLibraryElement curr= (CPUserLibraryElement) selected.get(i);
			if (fUsedPaths.contains(curr.getPath())) {
				return new StatusInfo(IStatus.ERROR, NewWizardMessages.getString("UserLibraryWizardPage.error.alreadyoncp")); //$NON-NLS-1$
			}
		}
		return new StatusInfo();
	}
	
	private void doButtonPressed(int index) {
		if (index == 0) {
			String currLibrary= fEditResult != null ? fEditResult.getName() : null;
			
			UserLibraryPreferencePage page= new UserLibraryPreferencePage(currLibrary, false);
			PreferencePageSupport.showPreferencePage(getShell(), UserLibraryPreferencePage.ID, page);
	
			List newEntries= updateLibraryList();
			if (newEntries.size() > 0) {
				if (fIsEditMode) {
					fLibrarySelector.setChecked(newEntries.get(0), true);
				} else {
					fLibrarySelector.setCheckedElements(newEntries);
				}
			}
		} else {
			fLibrarySelector.setCheckedElements(fLibrarySelector.getSelectedElements());
		}
	}
	
	private void doDoubleClicked(ListDialogField field) {
		List list= fLibrarySelector.getSelectedElements();
		if (list.size() == 1) {
			Object elem= list.get(0);
			boolean state= fLibrarySelector.isChecked(elem);
			if (!state || !fIsEditMode) {
				fLibrarySelector.setChecked(elem, !state);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		Composite composite= new Composite(parent, SWT.NONE);
		LayoutUtil.doDefaultLayout(composite, new DialogField[] { fLibrarySelector }, true);
		LayoutUtil.setHorizontalGrabbing(fLibrarySelector.getListControl(null));
		Dialog.applyDialogFont(composite);
		setControl(composite);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.IClasspathContainerPage#finish()
	 */
	public boolean finish() {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.IClasspathContainerPage#getSelection()
	 */
	public IClasspathEntry getSelection() {
		if (fEditResult != null) {
			return JavaCore.newContainerEntry(fEditResult.getPath());
		}
		return null;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.IClasspathContainerPageExtension2#getNewContainers()
	 */
	public IClasspathEntry[] getNewContainers() {
		List selected= fLibrarySelector.getCheckedElements();
		IClasspathEntry[] res= new IClasspathEntry[selected.size()];
		for (int i= 0; i < res.length; i++) {
			CPUserLibraryElement curr= (CPUserLibraryElement) selected.get(i);
			res[i]= JavaCore.newContainerEntry(curr.getPath());
		}
		return res;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.IClasspathContainerPage#setSelection(org.eclipse.jdt.core.IClasspathEntry)
	 */
	public void setSelection(IClasspathEntry containerEntry) {
		updateDescription(containerEntry);
		fIsEditMode= (containerEntry != null);
		if (containerEntry != null) {
			fUsedPaths.remove(containerEntry.getPath());
		}
		
		String selected= null;
		if (containerEntry != null && containerEntry.getPath().segmentCount() == 2) {
			selected= containerEntry.getPath().segment(1);
		} else {
			// get from dialog store
		}
		updateLibraryList();
		if (selected != null) {
			List elements= fLibrarySelector.getElements();
			for (int i= 0; i < elements.size(); i++) {
				CPUserLibraryElement curr= (CPUserLibraryElement) elements.get(i);
				if (curr.getName().equals(selected)) {
					fLibrarySelector.setChecked(curr, true);
					return;
				}
			}
		}
	}
	
	private class LibraryListAdapter implements IListAdapter, IDialogFieldListener {
		
		public LibraryListAdapter() {
		}
		
		public void dialogFieldChanged(DialogField field) {
			doDialogFieldChanged(field);
		}

		public void customButtonPressed(ListDialogField field, int index) {
			doButtonPressed(index);
		}

		public void selectionChanged(ListDialogField field) {
		}

		public void doubleClicked(ListDialogField field) {
			doDoubleClicked(field);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ui.wizards.IClasspathContainerPageExtension#initialize(org.eclipse.jdt.core.IJavaProject, org.eclipse.jdt.core.IClasspathEntry[])
	 */
	public void initialize(IJavaProject project, IClasspathEntry[] currentEntries) {
		for (int i= 0; i < currentEntries.length; i++) {
			IClasspathEntry curr= currentEntries[i];
			if (curr.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
				fUsedPaths.add(curr.getPath());
			}
		}
	}
}
