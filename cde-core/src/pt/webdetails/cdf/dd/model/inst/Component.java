/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.webdetails.cdf.dd.model.inst;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import pt.webdetails.cdf.dd.model.core.KnownThingKind;
import pt.webdetails.cdf.dd.model.core.validation.RequiredAttributeError;
import pt.webdetails.cdf.dd.model.core.validation.ValidationError;
import pt.webdetails.cdf.dd.model.core.validation.ValidationException;
import pt.webdetails.cdf.dd.model.inst.validation.ComponentDuplicatePropertyBindingError;
import pt.webdetails.cdf.dd.model.inst.validation.ComponentUnresolvedPropertyBindingError;
import pt.webdetails.cdf.dd.model.meta.ComponentType;
import pt.webdetails.cdf.dd.model.meta.MetaModel;

/**
 * @author dcleao
 */
public abstract class Component<TM extends ComponentType> extends Instance<TM>
{
  private static final Log _logger = LogFactory.getLog(Component.class);

  private final String _idPrefix;
  private final String _name; // cached
  
  private final Map<String, PropertyBinding> _propertyBindingsByLowerAlias;
  private final Map<String, PropertyBinding> _propertyBindingsByLowerName;
  
  private final List<ExtensionPropertyBinding> _extensionPropertyBindings;
  
  @SuppressWarnings({"LeakingThisInConstructor", "OverridableMethodCallInConstructor"})
  protected Component(Builder builder, final MetaModel metaModel) throws ValidationException
  {
    super(builder);

    assert metaModel != null;
    
    // NOTE: name may be empty.
    // During design time, components may not yet have a name.
    this._name = StringUtils.defaultIfEmpty(builder.tryGetComponentName(), "");
    
    this._idPrefix = builder._idPrefix == null ? this.initGetDefaultIdPrefix() : builder._idPrefix;
    
    if(this._name.isEmpty()) 
    {
      _logger.warn("A component of type '" + this.getMeta().getName() + "' has no name.");
    }
    
    if(builder.getPropertyBindingCount() > 0)
    {
      this._propertyBindingsByLowerAlias = new LinkedHashMap<String, PropertyBinding>();
      this._propertyBindingsByLowerName  = new LinkedHashMap<String, PropertyBinding>();
      this._extensionPropertyBindings    = new ArrayList<ExtensionPropertyBinding>();
      
      // Create PropertyBinding.s
      for(PropertyBinding.Builder bindBuilder : builder.getPropertyBindings())
      {
        PropertyBinding bind;
        try
        {
          bind = bindBuilder.build(this, metaModel);
        }
        catch(ValidationException ex)
        {
          // Ignore PropertyBinding, log warning and continue.
          
          // At least these errors are included:
          // * ComponentUnresolvedPropertyBindingError
          // * RequiredAttributeError
          
          _logger.warn(ex.getError());
          continue;
        }

        String propAlias = bind.getAlias().toLowerCase();
        if(this._propertyBindingsByLowerAlias.containsKey(propAlias))
        {
          _logger.warn(
              new ComponentDuplicatePropertyBindingError(
                  bind.getAlias(),
                  this.getId(),
                  this.getMeta().getLabel()));
          continue;
        }
        
        String propName = bind.getName().toLowerCase();
        this._propertyBindingsByLowerAlias.put(propAlias, bind);
        this._propertyBindingsByLowerName .put(propName,  bind);
        
        if(bind instanceof ExtensionPropertyBinding)
        {
          this._extensionPropertyBindings.add((ExtensionPropertyBinding)bind);
        }
      }
    }
    else
    {
      this._propertyBindingsByLowerAlias = null;
      this._propertyBindingsByLowerName  = null;
      this._extensionPropertyBindings    = null;
    }
  }
  
  /**
   * NOTE: called from within constructor.
   */
  protected String initGetDefaultIdPrefix()
  {
    return "";
  }
  
  public String buildId(String alias)
  {
    String unprefixedId = composeIds(alias, this._name);
    
    String pprefix = StringUtils.defaultIfEmpty(this._idPrefix, "");
    if(pprefix.length() > 0) { pprefix += "_"; }

    return pprefix + unprefixedId;
  }
  
  public static String composeIds(String alias, String localId)
  {
   final String plocalId = StringUtils.isEmpty(localId) ? "" : localId.replace(" ", "_");

    // Do not alias Dashboard.storage ids
    // TODO: explain this!!!
    String palias;
    if(plocalId.startsWith("Dashboards.storage")) 
    {
      palias = "";
    }
    else
    {
      palias = StringUtils.defaultIfEmpty(alias,  "");
      if(palias.length() > 0) { palias += "_"; }
    }
    
    return palias + localId;
  }
  
  @Override
  public TM getMeta()
  {
    return super.getMeta();
  }
  
  @Override
  public final String getId()
  {
    return this.buildId("");
  }

  public final String getIdPrefix()
  {
    return this._idPrefix;
  }

  public final String getName()
  {
    return this._name;
  }

  @Override
  public String getKind()
  {
    return KnownThingKind.Component;
  }

  public PropertyBinding  getPropertyBinding(String alias)
  {
    PropertyBinding propBind = this.tryGetPropertyBinding(alias);
    if(propBind == null)
    {
      throw new IllegalArgumentException("There is no property binding for alias '" + alias + "'.");
    }

    return propBind;
  }
  
  public PropertyBinding  getPropertyBindingByName(String name)
  {
    PropertyBinding propBind = this.tryGetPropertyBindingByName(name);
    if(propBind == null)
    {
      throw new IllegalArgumentException("There is no property binding for name '" + name + "'.");
    }

    return propBind;
  }

  public String tryGetPropertyValueByName( String name, String defaultValue)
  {
    PropertyBinding bind = this.tryGetPropertyBindingByName( name );
    return bind == null ?
      defaultValue :
      StringUtils.defaultIfEmpty(bind.getValue(), defaultValue);
  }

  public PropertyBinding tryGetPropertyBinding(String alias)
  {
    if(StringUtils.isEmpty(alias)) { throw new IllegalArgumentException("alias"); }

    return this._propertyBindingsByLowerAlias.get(alias.toLowerCase());
  }
  
  public String tryGetPropertyValue(String alias, String defaultValue)
  {
    PropertyBinding bind = this.tryGetPropertyBinding(alias);
    return bind == null ? 
           defaultValue : 
           StringUtils.defaultIfEmpty(bind.getValue(), defaultValue);
  }
  
  public PropertyBinding tryGetPropertyBindingByName(String name)
  {
    if(StringUtils.isEmpty(name)) { throw new IllegalArgumentException("name"); }

    return this._propertyBindingsByLowerName.get(name.toLowerCase());
  }
  
  public Iterable<PropertyBinding> getPropertyBindings()
  {
    return this._propertyBindingsByLowerAlias != null ?
           this._propertyBindingsByLowerAlias.values() :
           Collections.<PropertyBinding> emptyList();
  }
  
  public int getPropertyBindingCount()
  {
    return this._propertyBindingsByLowerAlias != null ? this._propertyBindingsByLowerAlias.size() : 0;
  }
  
  public final Iterable<ExtensionPropertyBinding> getExtensionPropertyBindings()
  {
    return this._extensionPropertyBindings != null ?
           this._extensionPropertyBindings :
           Collections.<ExtensionPropertyBinding> emptyList();
  }
  
  public int getExtensionPropertyBindingCount()
  {
    return this._extensionPropertyBindings != null ? this._extensionPropertyBindings.size() : 0;
  }
          
  /**
   * Class to create and modify Component instances.
   */
  public static abstract class Builder extends Instance.Builder
  {
    private String _idPrefix;
    
    private List<PropertyBinding.Builder> _propBindings;
    
    public String getIdPrefix()
    {
      return this._idPrefix;
    }

    public Builder setIdPrefix(String idPrefix)
    {
      this._idPrefix = idPrefix;
      return this;
    }

    public Builder addPropertyBinding(PropertyBinding.Builder prop)
    {
      if(prop == null) { throw new IllegalArgumentException("prop"); }

      if(this._propBindings == null)
      {
        this._propBindings = new ArrayList<PropertyBinding.Builder>();
      }

      this._propBindings.add(prop);
      
      return this;
    }

    public Iterable<PropertyBinding.Builder> getPropertyBindings()
    {
      return this._propBindings != null ?
             this._propBindings :
             Collections.<PropertyBinding.Builder> emptyList();
    }

    public int getPropertyBindingCount()
    {
      return this._propBindings != null ? this._propBindings.size() : 0;
    }
    
    public String tryGetComponentName()
    {
      for(PropertyBinding.Builder bindBuilder : this.getPropertyBindings())
      {
        if("name".equalsIgnoreCase(bindBuilder.getAlias()))
        {
          return bindBuilder.getValue();
        }
      }

      return null;
    }
    
    public abstract Component build(MetaModel metaModel) throws ValidationException;
  }
}
