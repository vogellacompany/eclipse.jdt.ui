/*******************************************************************************
 * Copyright (c) 2000, orporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Dmitry Stalnov (dstalnov@fusionone.com) - contributed fix for
 *       bug "inline method - doesn't handle implicit cast" (see
 *       https://bugs.eclipse.org/bugs/show_bug.cgi?id=24941).
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.dom;

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.PrimitiveType.Code;

/**
 * Helper class to check if objects are assignable to each other.
 */
public class TypeRules {

	/**
	 * Tests if a two primitive types are assign compatible
	 * @param toAssignCode The binding of the type to assign
	 * @param definedTypeCode The type of the object that is assigned
	 * @return boolean Returns true if definedType = typeToAssign is true
	 */
	public static boolean canAssignPrimitive(PrimitiveType.Code toAssignCode, PrimitiveType.Code definedTypeCode) {
		//	definedTypeCode = typeCodeToAssign;
		if (toAssignCode == definedTypeCode) {
			return true;
		}
		if (definedTypeCode == PrimitiveType.BOOLEAN || toAssignCode == PrimitiveType.BOOLEAN) {
			return false;
		}
		if (definedTypeCode == PrimitiveType.CHAR && toAssignCode == PrimitiveType.BYTE) {
			return false;
		}
		return getTypeOrder(definedTypeCode) > getTypeOrder(toAssignCode);
	}
	
	/**
	 * Tests if a two types are assign compatible
	 * @param typeToAssign The binding of the type to assign
	 * @param definedType The type of the object that is assigned
	 * @return boolean Returns true if definedType = typeToAssign is true
	 */
	public static boolean canAssign(ITypeBinding typeToAssign, String definedType) {
		// definedType = typeToAssign;

		int arrStart= definedType.indexOf('[');
		if (arrStart != -1) {
			if (!typeToAssign.isArray()) {
				return false; // can not assign a non-array type 
			}
			int definedDim= definedType.length() - arrStart;
			int toAssignDim= typeToAssign.getDimensions() * 2;
			if (definedDim == toAssignDim) {
				definedType= definedType.substring(0, arrStart);
				typeToAssign= typeToAssign.getElementType();
				if (typeToAssign.isPrimitive() && !definedType.equals(typeToAssign.getName())) {
					return false; // can't assign arrays of primitive types to each other
				}
				return canAssign(typeToAssign, definedType);
			} else if (definedDim < toAssignDim) {
				return isArrayCompatible(definedType.substring(0, arrStart));
			}
			return false;
		}

		PrimitiveType.Code definedTypeCode= PrimitiveType.toCode(definedType);
		if (typeToAssign.isPrimitive()) {
			if (definedTypeCode == null) {
				return false;
			}
			PrimitiveType.Code toAssignCode= PrimitiveType.toCode(typeToAssign.getName());
			return canAssignPrimitive(toAssignCode, definedTypeCode);
		} else {
			if (definedTypeCode != null) {
				return false;
			}
			if (typeToAssign.isArray()) {
				return isArrayCompatible(definedType);
			}
			if ("java.lang.Object".equals(definedType)) { //$NON-NLS-1$
				return true;
			}
			return (Bindings.findTypeInHierarchy(typeToAssign, definedType) != null);
		}
	}
	
	/**
	 * Tests if a two types are assign compatible. Void types are vener compatible.
	 * @param typeToAssign The binding of the type to assign
	 * @param definedType The type of the object that is assigned
	 * @return boolean Returns true if definedType = typeToAssign is true
	 */
	public static boolean canAssign(ITypeBinding typeToAssign, ITypeBinding definedType) {
		// definedType = typeToAssign;
		
		String voidName= PrimitiveType.VOID.toString();
		if (voidName.equals(typeToAssign.getName()) || voidName.equals(definedType.getName())) {
			return false;
		}

		if (typeToAssign.isNullType()) {
			return !definedType.isPrimitive();
		}
		if (definedType.isArray()) {
			if (!typeToAssign.isArray()) {
				return false; // can not assign a non-array type to an array
			}
			int definedDim= definedType.getDimensions();
			int toAssignDim= typeToAssign.getDimensions();
			if (definedDim == toAssignDim) {
				definedType= definedType.getElementType();
				typeToAssign= typeToAssign.getElementType();
				if (typeToAssign.isPrimitive() && typeToAssign != definedType) {
					return false; // can't assign arrays of different primitive types to each other
				}
				// fall through
			} else if (definedDim < toAssignDim) {
				return isArrayCompatible(definedType.getElementType());
			} else {
				return false;
			}
		}

		if (typeToAssign.isPrimitive()) {
			if (!definedType.isPrimitive()) {
				return false;
			}
			PrimitiveType.Code toAssignCode= PrimitiveType.toCode(typeToAssign.getName());
			PrimitiveType.Code definedTypeCode= PrimitiveType.toCode(definedType.getName());
			return canAssignPrimitive(toAssignCode, definedTypeCode);
		} else {
			if (definedType.isPrimitive()) {
				return false;
			}

			if (typeToAssign.isArray()) {
				return isArrayCompatible(definedType);
			}
			if (isJavaLangObject(definedType)) { //$NON-NLS-1$
				return true;
			}
			return Bindings.isSuperType(definedType, typeToAssign);
		}
	}

	private static int getTypeOrder(Code type) {
		if (type == PrimitiveType.BYTE)
			return 2;
		if (type == PrimitiveType.CHAR)
			return 3;
		if (type == PrimitiveType.SHORT)
			return 3;
		if (type == PrimitiveType.INT)
			return 4;
		if (type == PrimitiveType.LONG)
			return 5;
		if (type == PrimitiveType.FLOAT)
			return 6;
		if (type == PrimitiveType.DOUBLE)
			return 7;
		return 0;
	}

	private static boolean isArrayCompatible(String definedType) {
		return "java.lang.Object".equals(definedType) || "java.io.Serializable".equals(definedType) || "java.lang.Cloneable".equals(definedType); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
	
	public static boolean isArrayCompatible(ITypeBinding definedType) {
		if (definedType.isTopLevel()) {
			if (definedType.isClass()) {
				return "Object".equals(definedType.getName()) && "java.lang".equals(definedType.getPackage().getName());  //$NON-NLS-1$//$NON-NLS-2$
			} else {
				String qualifiedName= definedType.getQualifiedName();
				return "java.io.Serializable".equals(qualifiedName) || "java.lang.Cloneable".equals(qualifiedName); //$NON-NLS-1$ //$NON-NLS-2$
			}
		} 
		return false;
	}
	
	public static boolean isJavaLangObject(ITypeBinding definedType) {
		return definedType.isTopLevel() && definedType.isClass() && "Object".equals(definedType.getName()) && "java.lang".equals(definedType.getPackage().getName());  //$NON-NLS-1$//$NON-NLS-2$	
	}

	/**
	 * Tests if a two types are cast compatible
	 * @param castType The binding of the type to cast to
	 * @param bindingToCast The binding ef the expression to cast.
	 * @return boolean Returns true if (castType) bindingToCast is a valid cast expression (can be unnecessary, but not invalid).
	 */
	public static boolean canCast(ITypeBinding castType, ITypeBinding bindingToCast) {
		String voidName= PrimitiveType.VOID.toString();
		
		if (castType.isAnonymous() || castType.isNullType() || voidName.equals(castType.getName())) {
			throw new IllegalArgumentException();
		}
		
		if (castType == bindingToCast) {
			return true;
		}
		
		if (voidName.equals(bindingToCast.getName())) {
			return false;
		}
		
		if (bindingToCast.isArray()) {
			if (!castType.isArray()) {
				return isArrayCompatible(castType); // can not cast an arraytype to a non array type (except to Object, Serializable...)
			}

			int toCastDim= bindingToCast.getDimensions();
			int castTypeDim= castType.getDimensions();
			if (toCastDim == castTypeDim) {
				bindingToCast= bindingToCast.getElementType();
				castType= castType.getElementType();
				if (castType.isPrimitive() && castType != bindingToCast) {
					return false; // can't assign arrays of different primitive types to each other
				}
				// fall through
			} else if (toCastDim < castTypeDim) {
				return isArrayCompatible(bindingToCast.getElementType());
			} else {
				return isArrayCompatible(castType.getElementType());
			}
		}
		if (castType.isPrimitive()) {
			if (!bindingToCast.isPrimitive()) {
				return false;
			}
			String boolName= PrimitiveType.BOOLEAN.toString();
			return (!boolName.equals(castType.getName()) && !boolName.equals(bindingToCast.getName()));
		} else {
			if (bindingToCast.isPrimitive()) {
				return false;
			}
			if (castType.isArray()) {
				return isArrayCompatible(bindingToCast);
			}
			
			if (castType.isInterface() || bindingToCast.isInterface() || isJavaLangObject(castType)) {
				return true;
			}
			
			return Bindings.isSuperType(bindingToCast, castType) || Bindings.isSuperType(castType, bindingToCast);
		}
	}
	
}
