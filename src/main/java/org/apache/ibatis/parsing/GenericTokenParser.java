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
package org.apache.ibatis.parsing;

/**
 * @author Clinton Begin
 */
/**
  通用的token解析器
*/
public class GenericTokenParser {
  // 开始的 Token 字符串
  private final String openToken;
  // 结束的 Token 字符串
  private final String closeToken;
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }
    /**
     * 主要的解析方法
    */
  public String parse(String text) {
      // 校验是否非空
    if (text == null || text.isEmpty()) {
      return "";
    }
    // search open token
      // 查找开始的 openToken 的位置
    int start = text.indexOf(openToken, 0);
    if (start == -1) {
      return text;
    }
    char[] src = text.toCharArray();
    // 起始的查找位置
    int offset = 0;
    final StringBuilder builder = new StringBuilder();
    // openToken和endToken 中间的表达式
    StringBuilder expression = null;
    // 持续寻找 openToken 的位置,知道再也找不到 openToken 为止
    while (start > -1) {
        // 假如 openToken 之前还有 \\ 说明它被转义了，于是就去掉转义符
      if (start > 0 && src[start - 1] == '\\') {
        // this open token is escaped. remove the backslash and continue.
        builder.append(src, offset, start - offset - 1).append(openToken);
        offset = start + openToken.length();
      } else {
          // 并非是转义字符开始的话
        // found open token. let's search close token.
          // 因为有多次调用，所以需每次都重置该对象
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          expression.setLength(0);
        }
        // 将 offset 和 openToken 之间的内容，添加到 builder 中
        builder.append(src, offset, start - offset);
        // 之后将偏移量修改到 openToken 之后
        offset = start + openToken.length();
        // 查找 endToken 的位置
        int end = text.indexOf(closeToken, offset);
        // 持续将endToken之间的内容添加到 builder 中间, 直到找不到 endToken 为止
        while (end > -1) {
            // 查看 endToken 是否被转义了
          if (end > offset && src[end - 1] == '\\') {
            // this close token is escaped. remove the backslash and continue.
            expression.append(src, offset, end - offset - 1).append(closeToken);
            offset = end + closeToken.length();
            end = text.indexOf(closeToken, offset);
          } else {
              // 将偏移量和 endToken 之间的内容加入
            expression.append(src, offset, end - offset);
            offset = end + closeToken.length();
            break;
          }
        }
        // endToken 没找到的话，直接将剩下的内容全部写入
        if (end == -1) {
          // close token was not found.
          builder.append(src, start, src.length - start);
          offset = src.length;
        } else {
            // 有的话，将内容提交给 token 处理器处理，比如将动态的内容转换为 指定的内容之类的
          builder.append(handler.handleToken(expression.toString()));
          offset = end + closeToken.length();
        }
      }

      // 开始下一轮的寻找 openToken 的循环
      start = text.indexOf(openToken, offset);
    }
    // 偏移量超过字符串长度的时候，将剩下的全部写入
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
