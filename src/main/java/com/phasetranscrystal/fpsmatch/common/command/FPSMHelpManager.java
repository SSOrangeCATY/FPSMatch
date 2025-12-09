package com.phasetranscrystal.fpsmatch.common.command;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 帮助管理器，统一管理所有帮助信息
 */
public class FPSMHelpManager {
    private static final Map<Integer, CommandNode> cache = new ConcurrentHashMap<>();

    // 换行符常量
    private static final MutableComponent NEWLINE = Component.literal("\n");

    private record Parameter(String name, boolean required) { }

    // 命令节点类，用于构建命令树
    private static class CommandNode {
        private boolean isRoot = false;
        private int hashCache = 0;
        private boolean hashValid = false;
        
        private final String name;
        private final MutableComponent description;
        private final Map<String, CommandNode> children;

        @Nullable
        private MutableComponent hoverText;
        
        // 参数列表，用于存储命令参数
        private final List<Parameter> parameters = new ArrayList<>();
        
        // 展开/闭合状态
        private boolean expanded = true;

        public CommandNode(String name, MutableComponent description, boolean isRoot) {
            this.name = name;
            this.description = description;
            this.children = new TreeMap<>();
            this.isRoot = isRoot;
            invalidateHash();
        }

        public CommandNode(String name, MutableComponent description) {
            this.name = name;
            this.description = description;
            this.children = new TreeMap<>();
            invalidateHash();
        }

        public CommandNode(String name, MutableComponent description, @Nullable MutableComponent hoverText) {
            this.name = name;
            this.description = description;
            this.children = new TreeMap<>();
            this.hoverText = hoverText;
            invalidateHash();
        }
        
        public void addChild(CommandNode child) {
            children.put(child.name, child);
            invalidateHash();
        }
        
        public CommandNode getChild(String name) {
            return children.get(name);
        }
        
        public String getName() {
            return name;
        }
        
        public MutableComponent getDescription() {
            return description;
        }
        
        public Collection<CommandNode> getChildren() {
            return children.values();
        }
        
        // 添加参数
        public void addParameter(String parameter, boolean required) {
            parameters.add(new Parameter(parameter, required));
            invalidateHash();
        }

        public void setHoverText(MutableComponent hoverText) {
            this.hoverText = hoverText;
            invalidateHash();
        }

        @Nullable
        public MutableComponent getHoverText() {
            return hoverText;
        }

        // 获取参数列表
        public List<Parameter> getParameters() {
            return parameters;
        }
        
        // 获取展开状态
        public boolean isExpanded() {
            return expanded;
        }
        
        // 设置展开状态
        public void setExpanded(boolean expanded) {
            this.expanded = expanded;
        }
        
        // 切换展开状态
        public void toggleExpanded() {
            this.expanded = !this.expanded;
        }
        
        @Override
        public int hashCode() {
            return computeHash();
        }

        // 计算节点的Hash值
        public int computeHash() {
            if (!hashValid) {
                if (hashCache != 0) {
                    cache.remove(hashCache);
                }
                int result = 31;
                result = 31 * result + name.hashCode();
                result = 31 * result + description.getString().hashCode();
                result = 31 * result + (hoverText != null ? hoverText.getString().hashCode() : 0);
                
                // 计算参数的Hash
                for (Parameter param : parameters) {
                    result = 31 * result + param.name.hashCode();
                    result = 31 * result + (param.required ? 1 : 0);
                }
                
                // 计算子节点的Hash
                for (CommandNode child : children.values()) {
                    result = 31 * result + child.computeHash();
                }

                if(!isRoot && children.size() > 1){
                    expanded = false;
                }

                hashCache = result;
                cache.put(result, this);
                hashValid = true;
            }
            return hashCache;
        }
        
        // 使Hash缓存失效
        private void invalidateHash() {
            hashValid = false;
            computeHash();
        }
        
        //递归构建完整路径
        public String getFullPath() {
            StringBuilder sb = new StringBuilder();
            if (sb.isEmpty()) {
                sb.append(name);
            } else {
                sb.insert(0, name + " ");
            }
            return sb.toString();
        }
    
    }
    
    // 命令树的根节点
    private final CommandNode rootNode;
    
    // 单例实例
    private static final FPSMHelpManager INSTANCE = new FPSMHelpManager();
    
    /**
     * 获取HelpManager实例
     */
    public static FPSMHelpManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 私有构造方法，初始化命令树
     */
    private FPSMHelpManager() {
        this.rootNode = new CommandNode("fpsm", Component.translatable("commands.fpsm.help.header"), true);

        rootNode.addChild(new CommandNode("help", Component.translatable("commands.fpsm.help.basic.help")));
    }

    /**
     * 注册命令帮助节点
     * @param commandPath 命令路径，如 "fpsm save"
     * @param description 命令描述
     * */
    public void registerCommandHelp(String commandPath, MutableComponent description) {
        this.registerCommandHelp(commandPath, description, null);
    }

    /**
     * 注册命令帮助节点
     * @param commandPath 命令路径，如 "fpsm save"
     * @param description 命令描述
     * @param hoverText 命令悬停文本(可选)
     * */
    public void registerCommandHelp(String commandPath, MutableComponent description, MutableComponent hoverText) {
        String[] pathParts = commandPath.split(" ");
        CommandNode currentNode = rootNode;
        
        // 遍历命令路径，创建或获取节点
        int startIndex = pathParts[0].equals(rootNode.getName()) ? 1 : 0;
        
        for (int i = startIndex; i < pathParts.length; i++) {
            String part = pathParts[i];
            CommandNode child = currentNode.getChild(part);
            
            if (child == null) {
                child = new CommandNode(part, description, hoverText);
                currentNode.addChild(child);
            }


            currentNode = child;
        }
    }
    
    /**
     * 注册命令帮助节点
     * @param commandPath 命令路径，如 "fpsm save"
     * @param descriptionKey 命令描述的语言键
     */
    public void registerCommandHelp(String commandPath, String descriptionKey) {
        registerCommandHelp(commandPath, Component.translatable(descriptionKey));
    }

    /**
     * 注册命令帮助节点
     * @param commandPath 命令路径，如 "fpsm save"
     */
    public void registerCommandHelp(String commandPath) {
        registerCommandHelp(commandPath, Component.literal(""));
    }
    
    /**
     * 注册命令参数（支持单个参数可选标记，使用*前缀表示必选参数）
     * @param commandPath 命令路径，如 "fpsm tacz dummy"
     * @param parameters 参数列表，如 "*requiredParam", "optionalParam"
     */
    public void registerCommandParameters(String commandPath, String... parameters) {
        String[] pathParts = commandPath.split(" ");
        CommandNode currentNode = rootNode;
        
        // 遍历命令路径，找到目标节点
        int startIndex = pathParts[0].equals(rootNode.getName()) ? 1 : 0;
        
        for (int i = startIndex; i < pathParts.length; i++) {
            String part = pathParts[i];
            CommandNode child = currentNode.getChild(part);
            
            if (child == null) {
                // 如果节点不存在，创建空描述节点
                child = new CommandNode(part, Component.literal(""));
                currentNode.addChild(child);
            }
            
            currentNode = child;
        }
        
        // 添加参数，处理可选标记
        for (String param : parameters) {
            boolean required = param.startsWith("*");
            String paramName = required ? param.substring(1) : param;
            currentNode.addParameter(paramName, required);
        }
    }
    
    /**
     * 构建单个命令节点的显示格式
     * @param node 当前命令节点
     * @param indent 当前缩进
     * @param isRoot 是否为根节点
     * @return 格式化后的命令节点组件
     */
    private MutableComponent buildCommandLine(CommandNode node, String indent, boolean isRoot) {
        if (isRoot) {
            // 根节点特殊处理
            return Component.literal("/" + node.name).withStyle(ChatFormatting.AQUA);
        } else {
            // 非根节点：显示命令和描述
            MutableComponent prefix = Component.literal(indent + "└─ ").withStyle(ChatFormatting.GRAY);
            MutableComponent commandName = Component.literal(node.name).withStyle(ChatFormatting.DARK_AQUA);
            MutableComponent displayLine = prefix.append(commandName);
            
            // 添加参数显示
            if (!node.getParameters().isEmpty()) {
                for (Parameter param : node.getParameters()) {
                    if (param.required()) {
                        displayLine.append(Component.literal(" <").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(param.name()).withStyle(ChatFormatting.WHITE))
                                .append(Component.literal("> ").withStyle(ChatFormatting.GRAY));
                    } else {
                        displayLine.append(Component.literal(" [").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(param.name()).withStyle(ChatFormatting.WHITE))
                                .append(Component.literal("] ").withStyle(ChatFormatting.GRAY));
                    }
                }
            }
            
            // 添加分隔符和描述
            displayLine.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                      .append(node.description.withStyle(ChatFormatting.GRAY));

            if (node.getChildren().size() > 1) {
                String toggleKey = node.isExpanded() ? "commands.fpsm.help.node.toggle.collapse" : "commands.fpsm.help.node.toggle.expand";
                displayLine.append(Component.literal(" [").withStyle(ChatFormatting.GRAY))
                        .append(Component.translatable(toggleKey).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("]").withStyle(ChatFormatting.GRAY));
            }

            Style style = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fpsm help toggle " + node.hashCode()));

            if(node.hoverText != null){
                style = style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, node.hoverText));
            }

            return displayLine.withStyle(style);
        }
    }
    
    /**
     * 递归构建命令树的字符串表示
     * @param node 当前命令节点
     * @param indent 当前缩进
     * @param commandPath 当前命令路径
     * @param result 结果列表
     */
    private void buildCommandTreeString(CommandNode node, String indent, String commandPath, List<MutableComponent> result) {
        boolean isRoot = indent.isEmpty();
        MutableComponent commandLine = buildCommandLine(node, indent, isRoot);
        result.add(commandLine.append(NEWLINE));
        
        // 递归处理子节点
        String childIndent = indent + "|   ";
        if (node.isExpanded()) {
            String fullCommandPath = commandPath.isEmpty() ? "/" + node.name : commandPath + " " + node.name;
            for (CommandNode child : node.getChildren()) {
                buildCommandTreeString(child, childIndent, fullCommandPath, result);
            }
        } else {
            result.add(Component.literal(indent + "|   " + "└─ ").withStyle(ChatFormatting.GRAY)
                      .append(Component.translatable("commands.fpsm.help.node.expand", node.getChildren().size())
                      .withStyle(ChatFormatting.GRAY))
                      .append(NEWLINE));
        }
    }
    
    /**
     * 获取命令树的字符串表示
     * @return 命令树列表
     */
    public List<MutableComponent> getCommandTree() {
        List<MutableComponent> result = new ArrayList<>();
        buildCommandTreeString(rootNode, "", "", result);
        return result;
    }
    
    /**
     * 切换指定节点的展开/闭合状态
     * @param hashCode 节点哈希值
     * @return 是否成功切换
     */
    public boolean toggleNodeExpanded(int hashCode) {
        CommandNode currentNode = cache.getOrDefault(hashCode, null);
        if (currentNode == null) {
            return false;
        }

        if (currentNode.getChildren().size() <= 1) {
            return false;
        }

        currentNode.toggleExpanded();
        return true;
    }
    
    /**
     * 构建帮助消息
     * @param header 帮助头部
     * @param entries 帮助条目列表
     */
    public MutableComponent buildHelpMessage(Component header, List<MutableComponent> entries) {
        MutableComponent helpMessage = Component.empty();
        // 添加头部
        helpMessage.append(header.copy()).append(NEWLINE);
        
        // 添加分隔线
        helpMessage.append(Component.literal("\n"));
        
        
        // 添加当前页的帮助内容
        if (entries.isEmpty()) {
            helpMessage.append(Component.translatable("commands.fpsm.help.no_entries")).append(NEWLINE);
        } else {
            for (MutableComponent entry : entries) {
                helpMessage.append(entry);
            }
        }
        
        return helpMessage;
    }
    
    /**
     * 构建命令树帮助消息
     * @return 构建好的命令树帮助消息
     */
    public MutableComponent buildCommandTreeHelp() {
        List<MutableComponent> commandTree = getCommandTree();
        return buildHelpMessage(Component.translatable("commands.fpsm.help.header"), commandTree);
    }
    
    /**
     * 动态添加子指令到指定命令路径
     * @param commandPath 命令路径，如 "fpsm map modify"
     * @param childName 子指令名称
     * @param description 子指令描述
     * @return 是否添加成功
     */
    public boolean addChildCommand(String commandPath, String childName, MutableComponent description) {
        String[] pathParts = commandPath.split(" ");
        CommandNode currentNode = rootNode;
        
        // 遍历命令路径，找到目标父节点
        for (String part : pathParts) {
            if (!part.equals(currentNode.getName())) {
                CommandNode child = currentNode.getChild(part);
                if (child == null) {
                    // 路径不存在，创建中间节点
                    child = new CommandNode(part, Component.literal(""));
                    currentNode.addChild(child);
                }
                currentNode = child;
            }
        }
        
        // 添加子指令
        CommandNode childNode = new CommandNode(childName, description);
        currentNode.addChild(childNode);
        return true;
    }

    public static String withTeamCapability(String command) {
        return "fpsm map modify team teams capability " + command;
    }

    public static String withMapCapability(String command) {
        return "fpsm map modify capability " + command;
    }
}