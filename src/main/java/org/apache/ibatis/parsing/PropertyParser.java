/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.parsing;

import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
/**
  动态属性解析器， 只有静态方法
*/
public class PropertyParser {

  private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";
  /**
   * The special property key that indicate whether enable a default value on placeholder.
   * <p>
   *   The default value is {@code false} (indicate disable a default value on placeholder)
   *   If you specify the {@code true}, you can specify key and default value on placeholder (e.g. {@code ${db.username:postgres}}).
   * </p>
   * @since 3.4.2
   */
  public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value";

  /**
   * The special property key that specify a separator for key and default value on placeholder.
   * <p>
   *   The default separator is {@code ":"}.
   * </p>
   * @since 3.4.2
   */
  public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator";

  private static final String ENABLE_DEFAULT_VALUE = "false";
  private static final String DEFAULT_VALUE_SEPARATOR = ":";

  // 只有静态方法，所以
  private PropertyParser() {
    // Prevent Instantiation
  }

  public static String parse(String string, Properties variables) {
      // 创建 token 处理器,
    VariableTokenHandler handler = new VariableTokenHandler(variables);
    // 创建 通用Token 解析器, 并使用 上述处理器，也就是要处理类似 ${...} 的 token
    GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
    // 返回处理结果
    return parser.parse(string);
  }

  private static class VariableTokenHandler implements TokenHandler {
      // 属性的映射
    private final Properties variables;
    // 是否开启默认值功能， 比如 ${fileName:Frank} 假如没有 fileName 这个值，就使用默认值 Frank
    private final boolean enableDefaultValue;
    // 默认值的分隔符, 上面的 :
    private final String defaultValueSeparator;

    private VariableTokenHandler(Properties variables) {
      this.variables = variables;
      // 从属性映射中获取，获取不到就使用默认值 false 和 :
      this.enableDefaultValue = Boolean.parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
      this.defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR, DEFAULT_VALUE_SEPARATOR);
    }

    private String getPropertyValue(String key, String defaultValue) {
      return (variables == null) ? defaultValue : variables.getProperty(key, defaultValue);
    }

    /**
        通用解析器获得了 中间的内容后，再此处理 比如 ${fileName} 中的 fileName
    */
    @Override
    public String handleToken(String content) {
      if (variables != null) {
        String key = content;
        // 假如有默认值的话
        if (enableDefaultValue) {
            // 查看 默认值分隔符的位置
          final int separatorIndex = content.indexOf(defaultValueSeparator);
          String defaultValue = null;
          // 假如字符串中存在默认值,就分别确定默认值和 key
          if (separatorIndex >= 0) {
              // 去掉默认值和默认值分隔符后key
            key = content.substring(0, separatorIndex);
            // 找到默认值
            defaultValue = content.substring(separatorIndex + defaultValueSeparator.length());
          }
          // 假如存在默认值
          if (defaultValue != null) {

            return variables.getProperty(key, defaultValue);
          }
          // 开启默认值功能但未设置默认值和没开启默认值功能的处理一样
        }
        if (variables.containsKey(key)) {
          return variables.getProperty(key);
        }
      }
      // 这里写死了呢, 通用的 token处理器那里是选择 openToken 和 endToken的 todo
      return "${" + content + "}";
    }
  }

}
