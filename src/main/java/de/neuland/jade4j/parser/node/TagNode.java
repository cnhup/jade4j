package de.neuland.jade4j.parser.node;

import de.neuland.jade4j.compiler.IndentWriter;
import de.neuland.jade4j.compiler.Utils;
import de.neuland.jade4j.exceptions.ExpressionException;
import de.neuland.jade4j.exceptions.JadeCompilerException;
import de.neuland.jade4j.expression.ExpressionHandler;
import de.neuland.jade4j.model.JadeModel;
import de.neuland.jade4j.template.JadeTemplate;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TagNode extends AttrsNode {
    private Node textNode;
    private static final String[] selfClosingTags = {"area", "base", "br", "col", "embed", "hr", "img", "input", "keygen", "link", "menuitem", "meta", "param", "source", "track", "wbr"};
    private static final String[] inlineTags = { "a", "abbr", "acronym", "b", "br", "code", "em", "font", "i", "img", "ins", "kbd", "map", "samp", "small", "span", "strong", "sub", "sup"};
    private boolean buffer = false;
    private ArrayList<String> classes = new ArrayList<String>();

    public TagNode() {
        this.block = new BlockNode();
    }

    public void setTextNode(Node textNode) {
        this.textNode = textNode;
    }

    public Node getTextNode() {
        return textNode;
    }

    public boolean hasTextNode() {
        return textNode != null;
    }

    public boolean isInline(){
        return ArrayUtils.indexOf(inlineTags,this.name) > -1;
    }
    private boolean isInline(Node node){
      // Recurse if the node is a block
      if (node instanceof BlockNode) return everyIsInline(node.getNodes());
      return node instanceof TextNode || (ArrayUtils.indexOf(inlineTags,node.getName()) > -1);
    }
    private boolean everyIsInline(LinkedList<Node> nodes){
        boolean multilineInlineOnlyTag = true;
          for (Node node : nodes) {
              if(!isInline(node))
                  multilineInlineOnlyTag = false;
          }
        return multilineInlineOnlyTag;
    }
    public boolean canInline (){
        Node block = this.getBlock();
        if(block==null)
            return true;
        LinkedList<Node> nodes = block.getNodes();


      // Empty tag
      if (nodes.size()==0) return true;

      // Text-only or inline-only tag
      if (1 == nodes.size()) return isInline(nodes.get(0));

      // Multi-line inline-only tag
      if (everyIsInline(nodes)) {
        for (int i = 1, len = nodes.size(); i < len; ++i) {
          if (nodes.get(i-1) instanceof TextNode && nodes.get(i) instanceof TextNode)
            return false;
        }
        return true;
      }

      // Mixed tag
      return false;
    };
    @Override
    public void execute(IndentWriter writer, JadeModel model, JadeTemplate template) throws JadeCompilerException {
        writer.increment();
        if ("pre".equals(this.name)) writer.setEscape(true);
        if(writer.isPp() && !isInline()){
            writer.prettyIndent(0,true);
        }
        if (isSelfClosing() || isSelfClosing(template)) {
            writer.append("<");
            writer.append(name);
            writer.append(attributes(model, template));
            if (isTerse(template)) {
                writer.append(">");
            }else {
                writer.append("/>");
            }
            if (hasBlock()) {
                //Fehlerbehandlung
            }

        }else {
            writer.append("<");
            writer.append(name);
            writer.append(attributes(model, template));
            writer.append(">");
            if (hasCodeNode()) {
                codeNode.execute(writer, model, template);
            }
            if (hasBlock()) {
                block.execute(writer, model, template);
            }
            // pretty print
            if (writer.isPp() && !isInline() && !"pre".equals(name) && !canInline()){
                writer.prettyIndent(0, true);
            }
            writer.append("</");
            writer.append(name);
            writer.append(">");

        }
        if ("pre".equals(this.name)) writer.setEscape(false);
        writer.decrement();
    }

    private boolean isEmpty() {
        return !hasBlock() && !hasTextNode() && !hasCodeNode();
    }

    public boolean isTerse(JadeTemplate template) {
        return isSelfClosing(template) && template.isTerse();
    }

    public boolean isSelfClosing(JadeTemplate template) {
        return !template.isXml() && ArrayUtils.contains(selfClosingTags, name);
    }

    private String attributes(JadeModel model, JadeTemplate template) {
        StringBuilder sb = new StringBuilder();

//        Map<String, Object> mergedAttributes = attmergeInheritedAttributes(model);
//        for (Map.Entry<String, Attr> entry : attributes.entrySet()) {
//            try {
//                sb.append(getAttributeString(entry.getKey(), entry.getValue(), model, template));
//            } catch (ExpressionException e) {
//                throw new JadeCompilerException(this, template.getTemplateLoader(), e);
//            }
//
//        }
        for (Attr attribute : attributes) {
            try {
                sb.append(getAttributeString(attribute.getName(), attribute, model, template));
            } catch (ExpressionException e) {
                throw new JadeCompilerException(this, template.getTemplateLoader(), e);
            }
        }
        if(!classes.isEmpty()){
            sb.append(" ").append("class");
            sb.append("=").append('"');
            sb.append(String.join(" ",classes));
            sb.append('"');
        }
        return sb.toString();
    }

    private String getAttributeString(String name, Attr attribute, JadeModel model, JadeTemplate template) throws ExpressionException {
        String key = name;
        boolean escaped = false;
//        if ("class".equals(key)) {
//          classes.push(attr.val);
//          classEscaping.push(attr.escaped);
//        } else if (isConstant(attr.val)) {
//          if (buffer) {
//            this.buffer(runtime.attr(key, toConstant(attr.val), escaped, this.terse));
//          } else {
//            var val = toConstant(attr.val);
//            if (key === 'style') val = runtime.style(val);
//            if (escaped && !(key.indexOf('data') === 0 && typeof val !== 'string')) {
//              val = runtime.escape(val);
//            }
//            buf.push(utils.stringify(key) + ': ' + utils.stringify(val));
//          }
//        } else {
//          if (buffer) {
//            this.bufferExpression('jade.attr("' + key + '", ' + attr.val + ', ' + utils.stringify(escaped) + ', ' + utils.stringify(this.terse) + ')');
//          } else {
//            var val = attr.val;
//            if (key === 'style') {
//              val = 'jade.style(' + val + ')';
//            }
//            if (escaped && !(key.indexOf('data') === 0)) {
//              val = 'jade.escape(' + val + ')';
//            } else if (escaped) {
//              val = '(typeof (jade_interp = ' + val + ') == "string" ? jade.escape(jade_interp) : jade_interp)';
//            }
//            buf.push(utils.stringify(key) + ': ' + val);
//          }
//        }

        String value = null;
        Object attributeValue = attribute.getValue();
        if("class".equals(key)) {
            if (attributeValue instanceof String) {
                escaped = attribute.isEscaped();
                value = getInterpolatedAttributeValue(name, attributeValue,escaped, model, template);
            } else if (attributeValue instanceof ExpressionString) {
                escaped = ((ExpressionString) attributeValue).isEscape();
                Object expressionValue = evaluateExpression((ExpressionString) attributeValue, model,template.getExpressionHandler());
                if (expressionValue != null && expressionValue.getClass().isArray()) {
                    StringBuffer s = new StringBuffer("");
                    boolean first = true;
                    if (expressionValue instanceof int[]) {
                        for (int o : (int[]) expressionValue) {
                            if (!first)
                                s.append(" ");
                            s.append(o);
                            first = false;
                        }
                    } else {
                        for (Object o : (Object[]) expressionValue) {
                            if (!first)
                                s.append(" ");
                            s.append(o.toString());
                            first = false;
                        }
                    }
                    value = s.toString();
                }
            }
            if(!StringUtils.isBlank(value))
                classes.add(value);
            return "";
//        }else if("id".equals(key)){
//            value = (String) attribute;
        }else if (attributeValue instanceof String) {
            escaped = attribute.isEscaped();
            value = getInterpolatedAttributeValue(name, attributeValue, escaped, model, template);
        } else if (attributeValue instanceof Boolean) {
            if ((Boolean) attributeValue) {
                value = name;
            } else {
                return "";
            }
            if (template.isTerse()) {
                value = null;
            }
        } else if (attributeValue instanceof ExpressionString) {
            escaped = ((ExpressionString) attributeValue).isEscape();
            Object expressionValue = evaluateExpression((ExpressionString) attributeValue, model, template.getExpressionHandler());
            if (expressionValue == null) {
                return "";
            }
            // TODO: refactor
            if (expressionValue instanceof Boolean) {
                if ((Boolean) expressionValue) {
                    value = name;
                } else {
                    return "";
                }
                if (template.isTerse()) {
                    value = null;
                }
            }else{
                value = expressionValue.toString();
                value = StringEscapeUtils.escapeHtml4(value);
            }
        } else if (attributeValue instanceof String) {
            value = (String) attributeValue;
//        } else {
//            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (name != null) {
            sb.append(" ").append(name);
            if (value != null) {
                sb.append("=").append('"');
                sb.append(value);
                sb.append('"');
            }
        }
        return sb.toString();
    }

    private Object evaluateExpression(ExpressionString attribute, JadeModel model, ExpressionHandler expressionHandler) throws ExpressionException {
        String expression = ((ExpressionString) attribute).getValue();
        Object result = expressionHandler.evaluateExpression(expression, model);
        if (result instanceof ExpressionString) {
            return evaluateExpression((ExpressionString) result, model, expressionHandler);
        }
        return result;
    }

    private String getInterpolatedAttributeValue(String name, Object attribute, boolean escaped, JadeModel model, JadeTemplate template)
            throws JadeCompilerException {
//        if (!preparedAttributeValues.containsKey(name)) {
//            preparedAttributeValues.put(name, Utils.prepareInterpolate((String) attribute, escaped));
//        }
        List<Object> prepared = Utils.prepareInterpolate((String) attribute, escaped);
        try {
            return Utils.interpolate(prepared, model,template.getExpressionHandler());
        } catch (ExpressionException e) {
            throw new JadeCompilerException(this, template.getTemplateLoader(), e);
        }
    }

    public boolean isBuffer() {
        return buffer;
    }

    public void setBuffer(boolean buffer) {
        this.buffer = buffer;
    }
}
