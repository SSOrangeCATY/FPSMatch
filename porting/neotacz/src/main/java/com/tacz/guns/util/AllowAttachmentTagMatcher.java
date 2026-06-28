package com.tacz.guns.util;

import com.tacz.guns.resource.CommonAssetsManager;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AllowAttachmentTagMatcher {
    private static final String TAG_PREFIX = "#";
    private static final Cache CACHE = new Cache();

    public record Cache(
            Map<Pair<Identifier, Identifier>, Boolean> allowAttachmentCache,
            Map<Pair<Identifier, Identifier>, Boolean> tagMatchCache
    ) {
        public Cache() {
            this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
        }
    }

    public static boolean match(Identifier gunId, Identifier attachmentId) {
        var key = Pair.of(gunId, attachmentId);
        return CACHE.allowAttachmentCache().computeIfAbsent(key, AllowAttachmentTagMatcher::match0);
    }

    public static boolean match0(Pair<Identifier, Identifier> record) {
        Identifier gunId = record.getLeft();
        Identifier attachmentId = record.getRight();
        Set<String> allowAttachmentTags = CommonAssetsManager.get().getAllowAttachmentTags(gunId);
        // 如果枪械对应的 allowAttachmentTags 为空，说明目前没有任何可以装的配件
        if (allowAttachmentTags == null || allowAttachmentTags.isEmpty()) {
            return false;
        }
        // 开始遍历 allowAttachmentTags，寻找配件 id
        AtomicBoolean searchSignal = new AtomicBoolean(false);
        treeSearch(allowAttachmentTags, attachmentId, searchSignal);
        return searchSignal.get();
    }

    /**
     * 匹配配件是否有指定的标签。
     * 目前内部用于独头弹特殊标签的判断，
     * 也能方便到外部（附属，整合包等）制作它们的特殊标签。
     *
     * @param tag tacz 配件标签
     * @param attachmentId 配件 id
     * @return 配件 id 是否有这个配件标签
     * @since 1.1.7
     */
    public static boolean matchTag(Identifier tag, Identifier attachmentId) {
        var key = Pair.of(tag, attachmentId);
        return CACHE.tagMatchCache().computeIfAbsent(key, AllowAttachmentTagMatcher::matchTag0);
    }

    public static boolean matchTag0(Pair<Identifier, Identifier> record) {
        Identifier tag = record.getLeft();
        Identifier attachmentId = record.getRight();
        Set<String> tagContent = CommonAssetsManager.get().getAttachmentTags(tag);
        // 如果 tag 对应的内容集为空，说明目前没有任何内容
        if (tagContent == null || tagContent.isEmpty()) {
            return false;
        }
        // 开始遍历内容集，寻找配件 id
        AtomicBoolean searchSignal = new AtomicBoolean(false);
        treeSearch(tagContent, attachmentId, searchSignal);
        return searchSignal.get();
    }

    private static void treeSearch(Set<String> tags, Identifier attachmentId, AtomicBoolean searchSignal) {
        // 开始遍历 tags，寻找配件 id
        for (String tag : tags) {
            // 如果是 tag，则去 attachment tag 寻找我们的东西
            if (tag.startsWith(TAG_PREFIX)) {
                Identifier tagId = Identifier.parse(tag.substring(TAG_PREFIX.length()));
                Set<String> attachmentTags = CommonAssetsManager.get().getAttachmentTags(tagId);
                // 如果检索的这个配件 tag 不为空，开始递归查找
                if (attachmentTags != null && !attachmentTags.isEmpty()) {
                    treeSearch(attachmentTags, attachmentId, searchSignal);
                }
            }
            // 如果是配件 id，直接对比
            else {
                Identifier matchAttachmentId = Identifier.parse(tag);
                if (attachmentId.equals(matchAttachmentId)) {
                    searchSignal.set(true);
                    return;
                }
            }
        }
    }

    public static void resetCache() {
        CACHE.allowAttachmentCache().clear();
        CACHE.tagMatchCache().clear();
    }
}
