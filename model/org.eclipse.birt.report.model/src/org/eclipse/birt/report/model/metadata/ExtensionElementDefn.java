/*******************************************************************************
 * Copyright (c) 2004 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.model.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.birt.report.model.core.StyledElement;
import org.eclipse.birt.report.model.elements.ReportDesignConstants;
import org.eclipse.birt.report.model.extension.IReportItemFactory;
import org.eclipse.birt.report.model.i18n.ThreadResources;
import org.eclipse.birt.report.model.util.StringUtil;

/**
 * Represents the extended definition based on our report item extension point.
 * This class only used for those extension definition from third-party, not the
 * BIRT-defined standard elements. The extension definition must include an
 * instance of
 * {@link org.eclipse.birt.report.model.extension.IReportItemFactory}. The
 * included IElmentFactory gives the information about the internal, model
 * properties of that extension, how to instantiate
 * {@link org.eclipse.birt.report.model.extension.IReportItem}and other
 * information.
 */

public class ExtensionElementDefn extends ElementDefn
{

	/**
	 * The element factory of the extended element.
	 */

	protected IReportItemFactory elementFactory = null;

	/**
	 * The list contains the hidden properties in plugin.xml
	 */

	protected List invisibleProps = new ArrayList( );

	/**
	 * The list contains the cached system properties.
	 */

	protected Map cachedSystemProps = null;

	/**
	 * Constructs the extended element with the internal and element factory.
	 * 
	 * @param name
	 *            the name of the extended element definition
	 * @param elementFactory
	 *            the element factory of the extended element
	 *  
	 */

	public ExtensionElementDefn( String name, IReportItemFactory elementFactory )
	{
		assert name != null;
		assert elementFactory != null;
		this.name = name;
		this.elementFactory = elementFactory;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.birt.report.model.metadata.ElementDefn#build()
	 */

	protected void build( ) throws MetaDataException
	{
		if ( isBuilt )
			return;

		if ( extendsFrom == null )
			extendsFrom = ReportDesignConstants.REPORT_ITEM;
		
		buildDefn( );

		// Handle parent-specific tasks.

		MetaDataDictionary dd = MetaDataDictionary.getInstance( );

		if ( extendsFrom != null )
		{
			parent = (ElementDefn)dd.getElement( extendsFrom );
			if ( parent == null )
				throw new MetaDataException(
						new String[]{extendsFrom, name},
						MetaDataException.DESIGN_EXCEPTION_ELEMENT_PARENT_NOT_FOUND );
			parent.build( );

			// Cascade the setting of whether this element has a style.
			// That is, once an element has a style, all derived elements
			// have that style whether the meta data file explicitly indicated
			// this or not.

			if ( parent.hasStyle( ) )
				hasStyle = true;
		}

		// If this element has added a style, then add the intrinsic
		// style property.

		if ( ( parent == null || !parent.hasStyle( ) ) && hasStyle )
		{
			SystemPropertyDefn prop = new SystemPropertyDefn( );
			prop.setName( StyledElement.STYLE_PROP );
			prop.setType( dd.getPropertyType( PropertyType.ELEMENT_REF_TYPE ) );

			prop.setDisplayNameID( "Element.ReportElement.style" ); //$NON-NLS-1$
			prop.setDetails( MetaDataConstants.STYLE_NAME );
			prop.setIntrinsic( true );
			addProperty( prop );
		}

		// Cache data for properties defined here. Note, done here so
		// we don't repeat the work for any style properties copied below.

		buildProperties( );

		// TODO: If this item has a style, copy the relevant style properties
		// onto
		// this element if it's leaf element.

		// This element cannot forbid user-defined properties if
		// its parent supports them.

		if ( parent != null && parent.allowsUserProperties( ) )
			supportsUserProperties = true;

		// If this element is abstract and has a parent, then the parent
		// must also be abstract.

		if ( isAbstract( ) && parent != null && !parent.isAbstract( ) )
			throw new MetaDataException( new String[]{name, parent.getName( )},
					MetaDataException.DESIGN_EXCEPTION_ILLEGAL_ABSTRACT_ELEMENT );

		// Cascade the name space ID.

		if ( parent != null )
		{
			nameSpaceID = parent.getNameSpaceID( );
		}

		// Validate that the name and name space options are consistent.

		if ( !isAbstract( ) )
		{
			if ( nameSpaceID == MetaDataConstants.NO_NAME_SPACE )
				nameOption = MetaDataConstants.NO_NAME;
			if ( nameSpaceID != MetaDataConstants.NO_NAME_SPACE
					&& nameOption == MetaDataConstants.NO_NAME )
				throw new MetaDataException( new String[]{name},
						MetaDataException.DESIGN_EXCEPTION_INVALID_NAME_OPTION );
		}

		// The user can't extend abstract elements (only the design schema
		// itself can extend abstract definitions. The user also cannot extend
		// items without a name because there is no way to reference such
		// elements.

		if ( nameOption == MetaDataConstants.NO_NAME || isAbstract( ) )
			allowExtend = false;

		buildSlots( );

		createCachedSystemProperties( );

		// TODO: check if the javaClass is valid for concrete element type

		isBuilt = true;
	}

	/*
	 * Returns the properties only defined in extension element.
	 * 
	 * @see org.eclipse.birt.report.model.metadata.ElementDefn#getProperties()
	 */

	public List getProperties( )
	{
		return getLocalProperties( );
	}

	/*
	 * Returns the property only defined in extension element.
	 * 
	 * @see org.eclipse.birt.report.model.metadata.ElementDefn#getProperty(java.lang.String)
	 */

	public IElementPropertyDefn getProperty( String propName )
	{
		assert propName != null;
		SystemPropertyDefn prop = (SystemPropertyDefn) this.properties
				.get( propName );
		if ( prop != null )
			return prop;
		return null;
	}

	/**
	 * Gets the element factory of the extended element.
	 * 
	 * @return the element factory of the extended element
	 */

	public IReportItemFactory getElementFactory( )
	{
		return this.elementFactory;
	}

	/*
	 * Returns the localized display name, if non-empty string can be found with
	 * resource key and <code> IMessages </code> . Otherwise, return name of
	 * this element definition.
	 * 
	 * @see org.eclipse.birt.report.model.metadata.ObjectDefn#getDisplayName()
	 */

	public String getDisplayName( )
	{
		if ( displayNameKey != null && elementFactory.getMessages( ) != null )
		{
			String displayName = elementFactory.getMessages( ).getMessage(
					displayNameKey, ThreadResources.getLocale( ) );
			if ( !StringUtil.isBlank( displayName ) )
				return displayName;
		}

		return getName( );
	}

	/**
	 * Adds an invisible property to the list.
	 * 
	 * @param propName
	 *            the property name
	 */

	protected void addInvisibleProperty( String propName )
	{
		invisibleProps.add( propName );
	}

	/**
	 * Creates the cached system properties for <code>PropertyInvisible</code>
	 * tags.
	 *  
	 */

	private void createCachedSystemProperties( )
	{
		// DO NOT override buildProperties(). It is only for semantic check.

		for ( Iterator iter = invisibleProps.iterator( ); iter.hasNext( ); )
		{
			String propName = (String) iter.next( );
			SystemPropertyDefn sysDefn = (SystemPropertyDefn)parent.getProperty( propName );

			if ( sysDefn != null && sysDefn.isVisible( ) )
			{
				if ( cachedSystemProps == null )
					cachedSystemProps = new HashMap( );

				SystemPropertyDefn defn = createPropertyDefn( sysDefn );
				defn.setVisible( false );
				cachedSystemProps.put( propName, defn );
			}
		}
	}

	/**
	 * Returns a cached system property definition.
	 * 
	 * @param propName
	 *            the property name
	 * 
	 * @return the <code>SystemPropertyDefn</code> of the corresponding
	 *         <code>propName</code>.
	 */

	public SystemPropertyDefn getCachedSystemProperty( String propName )
	{
		if ( cachedSystemProps == null )
			return null;

		return (SystemPropertyDefn) cachedSystemProps.get( propName );
	}

	/**
	 * Creates a new <code>SystemPropertyDefn</code> with a given
	 * <code>SystemPropertyDefn</code>.
	 * 
	 * @param defn
	 *            the given system property definition
	 * @return the new created <code>SystemPropertyDefn</code>
	 */

	private static SystemPropertyDefn createPropertyDefn(
			SystemPropertyDefn defn )
	{
		assert defn != null;

		SystemPropertyDefn newDefn = new SystemPropertyDefn( );

		// 10 members on the PropertyDefn

		newDefn.setDefault( defn.getDefault( ) );
		newDefn.setAllowedChoices( defn.getAllowedChoices( ) );
		newDefn.setDisplayNameID( defn.getDisplayNameID( ) );
		newDefn.setIntrinsic( defn.isIntrinsic( ) );
		newDefn.setExtended( defn.isExtended( ) );
		newDefn.setIsList( defn.isList( ) );
		newDefn.setName( defn.getName( ) );
		newDefn.setType( defn.getType( ) );
		newDefn.setValueValidator( defn.valueValidator );
		newDefn.details = defn.details;

		// 3 members on the ElementPropertyDefn

		newDefn.setStyleProperty( defn.isStyleProperty( ) );
		newDefn.setVisible( defn.isVisible( ) );
		newDefn.setGroupNameKey( defn.getGroupNameKey( ) );

		// 1 member on the SystemPropertyDefn

		newDefn.setStyleProperty( defn.isStyleProperty( ) );

		return newDefn;
	}
}