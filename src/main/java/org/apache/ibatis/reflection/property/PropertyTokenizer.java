/**
 *    Copyright 2009-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * @author Clinton Begin
 */
/**
  实现迭代器，属性分词器，支持迭代器式的访问方式
  比如 访问 order[0].item[0].name 的时候 可以拆成 order[0] item[0] name 三段
*/
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
  // 待解析的字符串
  private String name;
  // 索引的 name indexdName = name + ('[' + index + ']')?
  private final String indexedName;
  /*
  *   编号
  *   对于 name[0] index = 0
  *   对于 Map map[key] index = key
  * */
  private String index;
  // 剩余的字符串
  private final String children;

  public PropertyTokenizer(String fullname) {
    // "." 作为属性的分隔
    int delim = fullname.indexOf('.');
    if (delim > -1) {
      // 找出当前的属性名
      name = fullname.substring(0, delim);
      // 剩下的字符串
      children = fullname.substring(delim + 1);
    } else {
      // 没有 . 的话整个都是属性名了
      name = fullname;
      children = null;
    }
    indexedName = name;
    delim = name.indexOf('[');
    // 假如有 [ 的话，就分别赋值 index
    if (delim > -1) {
      index = name.substring(delim + 1, name.length() - 1);
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  // 看看还有没有剩下的字符串
  @Override
  public boolean hasNext() {
    return children != null;
  }

  // 获取下一个
  @Override
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }
}
