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
package org.eclipse.jdt.ui.wizards;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.util.Assert;

import org.eclipse.ui.dialogs.WizardNewProjectCreationPage;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.preferences.NewJavaProjectPreferencePage;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathsBlock;

/**
 * Standard wizard page for creating new Java projects. This page can be used in 
 * project creation wizards for projects and will configure the project with the 
 * Java nature. This page also allows the user to configure the Java project's 
 * output location for class files generated by the Java builder.
 * <p>
 * Whenever possible clients should use the class <code>JavaCapabilityConfigurationPage
 * </code> in favour of this class.
 * </p>
 */
public class NewJavaProjectWizardPage extends NewElementWizardPage {
	
	private static final String PAGE_NAME= "NewJavaProjectWizardPage"; //$NON-NLS-1$

	private WizardNewProjectCreationPage fMainPage;
	
	private IPath fOutputLocation;
	private IClasspathEntry[] fClasspathEntries;
	private BuildPathsBlock fBuildPathsBlock;

	private boolean fProjectModified;
	
	/**
	 * Creates a Java project wizard creation page.
	 * <p>
	 * The Java project wizard reads project name and location from the main page.
	 * </p>
	 *
	 * @param root the workspace root
	 * @param mainpage the main page of the wizard
	 */	
	public NewJavaProjectWizardPage(IWorkspaceRoot root, WizardNewProjectCreationPage mainpage) {
		super(PAGE_NAME);
		
		setTitle(NewWizardMessages.getString("NewJavaProjectWizardPage.title")); //$NON-NLS-1$
		setDescription(NewWizardMessages.getString("NewJavaProjectWizardPage.description")); //$NON-NLS-1$
		
		fMainPage= mainpage;
		IStatusChangeListener listener= new IStatusChangeListener() {
			public void statusChanged(IStatus status) {
				updateStatus(status);
			}
		};

		fBuildPathsBlock= new BuildPathsBlock(listener, 0);
		
		fProjectModified= true;
		fOutputLocation= null;
		fClasspathEntries= null;
	}		
	
	/**
	 * Sets the default output location to be used for the new Java project.
	 * This is the path of the folder (with the project) into which the Java builder 
	 * will generate binary class files corresponding to the project's Java source
	 * files.
	 * <p>
	 * The wizard will create this folder if required.
	 * </p>
	 * <p>
	 * The default classpath will be applied when <code>initBuildPaths</code> is
	 * called. This is done automatically when the page becomes visible and
	 * the project or the default paths have changed.
	 * </p>
	 *
	 * @param path the folder to be taken as the default output path
	 */
	public void setDefaultOutputFolder(IPath path) {
		fOutputLocation= path;
		setProjectModified();
	}	

	/**
	 * Sets the default classpath to be used for the new Java project.
	 * <p>
	 * The caller of this method is responsible for creating the classpath entries 
	 * for the <code>IJavaProject</code> that corresponds to the created project.
	 * The caller is responsible for creating any new folders that might be mentioned
	 * on the classpath.
	 * </p>
	 * <p>
	 * The default output location will be applied when <code>initBuildPaths</code> is
	 * called. This is done automatically when the page becomes visible and
	 * the project or the default paths have changed.
	 * </p>
	 *
	 * @param entries the default classpath entries
	 * @param appendDefaultJRE <code>true</code> a variable entry for the
	 *  default JRE (specified in the preferences) will be added to the classpath.
	 */
	public void setDefaultClassPath(IClasspathEntry[] entries, boolean appendDefaultJRE) {
		if (entries != null && appendDefaultJRE) {
			IClasspathEntry[] jreEntry= NewJavaProjectPreferencePage.getDefaultJRELibrary();
			IClasspathEntry[] newEntries= new IClasspathEntry[entries.length + jreEntry.length];
			System.arraycopy(entries, 0, newEntries, 0, entries.length);
			System.arraycopy(jreEntry, 0, newEntries, entries.length, jreEntry.length);
			entries= newEntries;
		}
		fClasspathEntries= entries;
		setProjectModified();
	}
	
	/**
	 * Sets the project state to modified. Doing so will initialize the page 
	 * the next time it becomes visible.
	 * 
	 * @since 2.0
	 */
	public void setProjectModified() {
		fProjectModified= true;
	}

	/**
	 * Returns the project handle. Subclasses should override this 
	 * method if they don't provide a main page or if they provide 
	 * their own main page implementation.
	 * 
	 * @return the project handle
	 */
	protected IProject getProjectHandle() {
		Assert.isNotNull(fMainPage);
		return fMainPage.getProjectHandle();
	}
	
	/**
	 * Returns the project locaction path. Subclasses should override this 
	 * method if they don't provide a main page or if they provide 
	 * their own main page implementation.
	 * 
	 * @return the project location path
	 */
	protected IPath getLocationPath() {
		Assert.isNotNull(fMainPage);
		return fMainPage.getLocationPath();
	}	

	/**
	 * Returns the Java project handle by converting the result of
	 * <code>getProjectHandle()</code> into a Java project.
	 *
	 * @return the Java project handle
	 * @see #getProjectHandle()
	 */	
	public IJavaProject getNewJavaProject() {
		return JavaCore.create(getProjectHandle());
	}	

	/* (non-Javadoc)
	 * @see WizardPage#createControl
	 */	
	public void createControl(Composite parent) {
		Control control= fBuildPathsBlock.createControl(parent);
		setControl(control);
		Dialog.applyDialogFont(control);
		
		WorkbenchHelp.setHelp(control, IJavaHelpContextIds.NEW_JAVAPROJECT_WIZARD_PAGE);
	}
	
	/**
	 * Forces the initialization of the Java project page. Default classpath or buildpath
	 * will be used if set. The initialization should only be performed when the project
	 * or default paths have changed. Toggeling back and forward the pages without 
	 * changes should not re-initialize the page, as changes from the user will be 
	 * overwritten.
	 * 
	 * @since 2.0
	 */
	protected void initBuildPaths() {
		fBuildPathsBlock.init(getNewJavaProject(), fOutputLocation, fClasspathEntries);
	} 

	/**
	 * Extend this method to set a user defined default classpath or output location.
	 * The method <code>initBuildPaths</code> is called when the page becomes 
	 * visible the first time or the project or the default paths have changed.
	 * 
	 * @param visible if <code>true</code> the page becomes visible; otherwise
	 * it becomes invisible
	 */	
	public void setVisible(boolean visible) {
		super.setVisible(visible);		
		if (visible) {
			// evaluate if a initialization is required
			if (fProjectModified || isNewProjectHandle()) {
				// only initialize the project when needed
				initBuildPaths();
				fProjectModified= false;
			}
		}
	}
	
	private boolean isNewProjectHandle() {
		IProject oldProject= fBuildPathsBlock.getJavaProject().getProject();
		return !oldProject.equals(getProjectHandle());
	}
	
	
	/**
	 * Returns the currently configured output location. Note that the returned path 
	 * might not be valid.
	 * 
	 * @return the configured output location
	 * 
	 * @since 2.0
	 */
	public IPath getOutputLocation() {
		return fBuildPathsBlock.getOutputLocation();
	}

	/**
	 * Returns the currently configured classpath. Note that the classpath might
	 * not be valid.
	 * 
	 * @return the configured classpath
	 * 
	 * @since 2.0
	 */	
	public IClasspathEntry[] getRawClassPath() {
		return fBuildPathsBlock.getRawClassPath();
	}
	

	/**
	 * Returns the runnable that will create the Java project. The runnable will create 
	 * and open the project if needed. The runnable will add the Java nature to the 
	 * project, and set the project's classpath and output location. 
	 * <p>
	 * To create the new java project, execute this runnable
	 * </p>
	 *
	 * @return the runnable
	 */		
	public IRunnableWithProgress getRunnable() {
		return new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				if (monitor == null) {
					monitor= new NullProgressMonitor();
				}				
				monitor.beginTask(NewWizardMessages.getString("NewJavaProjectWizardPage.op_desc"), 10); //$NON-NLS-1$
				// initialize if needed
				if (fProjectModified || isNewProjectHandle()) {
					initBuildPaths();
				}
				
				// create the project
				try {
					BuildPathsBlock.createProject(getProjectHandle(), getLocationPath(), new SubProgressMonitor(monitor, 2));
					BuildPathsBlock.addJavaNature(getProjectHandle(), new SubProgressMonitor(monitor, 2));
					fBuildPathsBlock.configureJavaProject(new SubProgressMonitor(monitor, 6));
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				} catch (OperationCanceledException e) {
					throw new InterruptedException();
				} finally {
					monitor.done();
				}
			}
		};	
	}
}
