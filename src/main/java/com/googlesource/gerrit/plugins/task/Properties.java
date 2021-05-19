// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.task;

import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.googlesource.gerrit.plugins.task.TaskConfig.NamesFactory;
import com.googlesource.gerrit.plugins.task.TaskConfig.Task;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Use to expand properties like ${_name} for a Task Definition. */
public class Properties {
  protected Map<String, String> expanded = new HashMap<>();
  protected Map<String, String> unexpanded;
  protected Set<String> expanding;
  protected Expander recursiveExpander;

  public Properties(ChangeData changeData, Task definition, Map<String, String> parentProperties)
      throws OrmException {
    expanded.putAll(parentProperties);
    expanded.put("_name", definition.name);
    Change c = changeData.change();
    expanded.put("_change_number", String.valueOf(c.getId().get()));
    expanded.put("_change_id", c.getKey().get());
    expanded.put("_change_project", c.getProject().get());
    expanded.put("_change_branch", c.getDest().get());
    expanded.put("_change_status", c.getStatus().toString());
    expanded.put("_change_topic", c.getTopic());

    unexpanded = definition.properties;
    unexpanded.putAll(definition.exported);
    recursiveExpander =
        new Expander(expanded) {
          @Override
          protected String getValueForName(String name) {
            expandProperty(name); // recursive call
            return super.getValueForName(name);
          }
        };
    expandAllUnexpanded();
    definition.properties = expanded;
    for (String property : definition.exported.keySet()) {
      definition.exported.put(property, expanded.get(property));
    }

    new Expander(expanded).expandFieldValues(definition, Collections.emptySet());
  }

  public Properties(NamesFactory namesFactory, Map<String, String> properties) {
    new Expander(properties).expandFieldValues(namesFactory, Sets.newHashSet(TaskConfig.KEY_TYPE));
  }

  protected void expandAllUnexpanded() {
    String property;
    // A traditional iterator won't work because the recursive expansion may end up
    // expanding more than one property per iteration behind the iterator's back.
    while ((property = getFirstUnexpandedProperty()) != null) {
      expanding = new HashSet<>();
      expandProperty(property);
    }
  }

  protected void expandProperty(String property) {
    if (!expanding.add(property)) {
      throw new RuntimeException("Looping property definitions.");
    }
    String value = unexpanded.remove(property);
    if (value != null) {
      expanded.put(property, recursiveExpander.expandText(value));
    }
  }

  protected String getFirstUnexpandedProperty() {
    try {
      return unexpanded.keySet().iterator().next();
    } catch (NoSuchElementException e) {
      return null;
    }
  }

  /**
   * Use to expand properties like ${property} in Strings into their values.
   *
   * <p>Given some property name/value asssociations defined like this:
   *
   * <p><code>
   * valueByName.put("animal", "fox");
   * valueByName.put("bar", "foo");
   * valueByName.put("obstacle", "fence");
   * </code>
   *
   * <p>a String like: <code>"The brown ${animal} jumped over the ${obstacle}."</code>
   *
   * <p>will expand to: <code>"The brown fox jumped over the fence."</code>
   */
  protected static class Expander {
    // "${_name}" -> group(1) = "_name"
    protected static final Pattern PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    protected final Map<String, String> valueByName;

    public Expander(Map<String, String> valueByName) {
      this.valueByName = valueByName;
    }

    /** Expand all properties in the Strings in the object's Fields (except the exclude ones) */
    protected void expandFieldValues(Object object, Set<String> excludedFieldNames) {
      for (Field field : object.getClass().getFields()) {
        try {
          if (!excludedFieldNames.contains(field.getName())) {
            field.setAccessible(true);
            Object o = field.get(object);
            if (o instanceof String) {
              field.set(object, expandText((String) o));
            } else if (o instanceof List) {
              @SuppressWarnings("unchecked")
              List<String> forceCheck = List.class.cast(o);
              expandElements(forceCheck);
            }
          }
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }
    }

    /** Expand all properties in the Strings in the List */
    public void expandElements(List<String> list) {
      if (list != null) {
        for (ListIterator<String> it = list.listIterator(); it.hasNext(); ) {
          it.set(expandText(it.next()));
        }
      }
    }

    /** Expand all properties (${property_name} -> property_value) in the given text */
    public String expandText(String text) {
      if (text == null) {
        return null;
      }
      StringBuffer out = new StringBuffer();
      Matcher m = PATTERN.matcher(text);
      while (m.find()) {
        m.appendReplacement(out, Matcher.quoteReplacement(getValueForName(m.group(1))));
      }
      m.appendTail(out);
      return out.toString();
    }

    /** Get the replacement value for the property identified by name */
    protected String getValueForName(String name) {
      String value = valueByName.get(name);
      return value == null ? "" : value;
    }
  }
}
