/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation 
 * 			(report 36180: Callers/Callees view)
 ******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.jdt.core.IJavaElement;

import org.eclipse.jdt.internal.corext.callhierarchy.MethodWrapper;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.ui.progress.DeferredTreeContentManager;

public class CallHierarchyContentProvider implements ITreeContentProvider {
    private final static Object[] EMPTY_ARRAY = new Object[0];

    private DeferredTreeContentManager fManager;
    private CallHierarchyViewPart fPart;
    
    private class MethodWrapperRunnable implements IRunnableWithProgress {
        private MethodWrapper fMethodWrapper;
        private MethodWrapper[] fCalls= null;

        MethodWrapperRunnable(MethodWrapper methodWrapper) {
            fMethodWrapper= methodWrapper;
        }
                
        public void run(IProgressMonitor pm) {
        	fCalls= fMethodWrapper.getCalls(pm);
        }
        
        MethodWrapper[] getCalls() {
            if (fCalls != null) {
                return fCalls;
            }
            return new MethodWrapper[0];
        }
    }

    public CallHierarchyContentProvider(CallHierarchyViewPart part) {
        super();
        fPart= part;
    }

    /**
     * @see org.eclipse.jface.viewers.ITreeContentProvider#getChildren(java.lang.Object)
     */
    public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof TreeRoot) {
            TreeRoot dummyRoot = (TreeRoot) parentElement;

            return new Object[] { dummyRoot.getRoot() };
        } else if (parentElement instanceof MethodWrapper) {
            MethodWrapper methodWrapper = ((MethodWrapper) parentElement);

            if (shouldStopTraversion(methodWrapper)) {
                return EMPTY_ARRAY;
            } else {
                if (fManager != null) {
                    Object[] children = fManager.getChildren(new DeferredMethodWrapper(this, methodWrapper));
                    if (children != null)
                        return children;
                }
                return fetchChildren(methodWrapper);            
            }
        }

        return EMPTY_ARRAY;
    }

    protected Object[] fetchChildren(MethodWrapper methodWrapper) {
        IRunnableContext context= JavaPlugin.getActiveWorkbenchWindow();
        MethodWrapperRunnable runnable= new MethodWrapperRunnable(methodWrapper);
        try {
            context.run(true, true, runnable);
        } catch (InvocationTargetException e) {
            ExceptionHandler.handle(e, CallHierarchyMessages.getString("CallHierarchyContentProvider.searchError.title"), CallHierarchyMessages.getString("CallHierarchyContentProvider.searchError.message"));  //$NON-NLS-1$ //$NON-NLS-2$
            return EMPTY_ARRAY;
        } catch (InterruptedException e) {
            return new Object[] { TreeTermination.SEARCH_CANCELED };
        }
        
        return runnable.getCalls();
    }

    private boolean shouldStopTraversion(MethodWrapper methodWrapper) {
        return (methodWrapper.getLevel() > CallHierarchyUI.getDefault().getMaxCallDepth()) || methodWrapper.isRecursive();
    }

    /**
     * @see org.eclipse.jface.viewers.IStructuredContentProvider#getElements(java.lang.Object)
     */
    public Object[] getElements(Object inputElement) {
        return getChildren(inputElement);
    }

    /**
     * @see org.eclipse.jface.viewers.ITreeContentProvider#getParent(java.lang.Object)
     */
    public Object getParent(Object element) {
        if (element instanceof MethodWrapper) {
            return ((MethodWrapper) element).getParent();
        }

        return null;
    }

    /**
     * @see org.eclipse.jface.viewers.IContentProvider#dispose()
     */
    public void dispose() {
        // Nothing to dispose
    }

    /**
     * @see org.eclipse.jface.viewers.ITreeContentProvider#hasChildren(java.lang.Object)
     */
    public boolean hasChildren(Object element) {
        if (element == TreeRoot.EMPTY_ROOT || element == TreeTermination.SEARCH_CANCELED) {
            return false;
        }

        // Only methods can have subelements, so there's no need to fool the user into believing that there is more
        if (element instanceof MethodWrapper) {
            MethodWrapper methodWrapper= (MethodWrapper) element;
            if (methodWrapper.getMember().getElementType() != IJavaElement.METHOD) {
                return false;
            }
            if (shouldStopTraversion(methodWrapper)) {
                return false;
            }
            return true;
        } else if (element instanceof TreeRoot) {
        	return true;
        }

        return false; // the "Update ..." placeholder has no children
    }

    /**
     * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
     */
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        cancelJobs();
        if (viewer instanceof AbstractTreeViewer) {
            fManager = new DeferredTreeContentManager(this, (AbstractTreeViewer) viewer, fPart.getSite());
        }
    }

    /**
     * Cancel all current jobs. 
     */
    void cancelJobs() {
        if (fManager != null) {
        	fManager.cancel(null);
            if (fPart != null) {
                fPart.setCancelEnabled(false);
            }
        }
    }

    /**
     * 
     */
    public void doneFetching() {
        if (fPart != null) {
            fPart.setCancelEnabled(false);
        }
    }

    /**
     * 
     */
    public void startFetching() {
        if (fPart != null) {
            fPart.setCancelEnabled(true);
        }
    }
}
