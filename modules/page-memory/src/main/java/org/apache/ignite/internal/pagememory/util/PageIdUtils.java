/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.pagememory.util;

import org.apache.ignite.internal.pagememory.FullPageId;
import org.apache.ignite.internal.pagememory.PageIdAllocator;
import org.apache.ignite.internal.util.IgniteUtils;

/**
 * Utility class for page ID parts manipulation.
 *
 * @see FullPageId
 */
public final class PageIdUtils {
    /** Size of the page index portion. */
    public static final int PAGE_IDX_SIZE = Integer.SIZE;

    /** Size of the partition ID portion. */
    public static final int PART_ID_SIZE = Short.SIZE;

    /** Size of the flags portion. */
    public static final int FLAG_SIZE = Byte.SIZE;

    /** Size of the offset portion. */
    public static final int OFFSET_SIZE = Byte.SIZE;

    /** Size of a tag portion. */
    public static final int TAG_SIZE = 2 * Byte.SIZE;

    /** Page index mask. */
    public static final long PAGE_IDX_MASK = ~(-1L << PAGE_IDX_SIZE);

    /** Offset mask. */
    public static final long OFFSET_MASK = ~(-1L << OFFSET_SIZE);

    /** Tag mask. */
    public static final long TAG_MASK = ~(-1L << TAG_SIZE);

    /** Page Index is a monotonically growing number within each partition. */
    public static final long PART_ID_MASK = ~(-1L << PART_ID_SIZE);

    /** Flags mask. Flags consists of a number of reserved bits, and page type (data/index page). */
    public static final long FLAG_MASK = ~(-1L << FLAG_SIZE);

    /** Effective page ID mask. */
    private static final long EFFECTIVE_PAGE_ID_MASK = ~(-1L << (PAGE_IDX_SIZE + PART_ID_SIZE));

    /**
     * Offset of a Rotation ID inside a Page ID.
     */
    private static final long ROTATION_ID_OFFSET = PAGE_IDX_SIZE + PART_ID_SIZE + FLAG_SIZE;

    /** Page ID mask that excludes link. */
    private static final long PAGE_ID_MASK = ~(-1L << ROTATION_ID_OFFSET);

    /** Max itemid number. */
    public static final int MAX_ITEMID_NUM = 0xFE;

    /** Maximum page number. */
    public static final long MAX_PAGE_NUM = (1L << PAGE_IDX_SIZE) - 1;

    /** Maximum page number. */
    public static final int MAX_PART_ID = (1 << PART_ID_SIZE) - 1;

    /** Private constructor. */
    private PageIdUtils() {
        // No-op.
    }

    /**
     * Constructs a page link by the given page ID and 8-byte words within the page.
     *
     * @param pageId Page ID.
     * @param itemId Item ID.
     * @return Page link.
     */
    public static long link(long pageId, int itemId) {
        assert itemId >= 0 && itemId <= MAX_ITEMID_NUM : itemId;
        assert (pageId >> ROTATION_ID_OFFSET) == 0 : IgniteUtils.hexLong(pageId);

        return pageId | (((long) itemId) << ROTATION_ID_OFFSET);
    }

    /**
     * Extracts a page index from the given page ID.
     *
     * @param pageId Page ID.
     * @return Page index.
     */
    public static int pageIndex(long pageId) {
        return (int) (pageId & PAGE_IDX_MASK); // 4 bytes
    }

    /**
     * Extracts a page ID from the given page link.
     *
     * @param link Page link.
     * @return Page ID.
     */
    public static long pageId(long link) {
        return flag(link) == PageIdAllocator.FLAG_DATA ? link & PAGE_ID_MASK : link;
    }

    /**
     * Creates page ID from its components.
     *
     * @param partitionId Partition ID.
     * @param flag        Flags (a number of reserved bits, and page type (data/index page))
     * @param pageIdx     Page index, monotonically growing number within each partition
     * @return Page ID constructed from the given pageIdx and partition ID, see {@link FullPageId}
     */
    public static long pageId(int partitionId, byte flag, int pageIdx) {
        long pageId = flag & FLAG_MASK;

        pageId = (pageId << PART_ID_SIZE) | (partitionId & PART_ID_MASK);
        pageId = (pageId << (PAGE_IDX_SIZE)) | (pageIdx & PAGE_IDX_MASK);

        return pageId;
    }

    /**
     * Converts link into an effective page ID: pageId with only pageIdx and partitionId.
     *
     * @param link Page link.
     * @return Effective page id.
     */
    public static long effectivePageId(long link) {
        return link & EFFECTIVE_PAGE_ID_MASK;
    }

    /**
     * Checks whether page ID matches effective page ID.
     *
     * @param pageId Page id.
     * @return {@code True} if page id is equal to effective page id.
     */
    public static boolean isEffectivePageId(long pageId) {
        return (pageId & ~EFFECTIVE_PAGE_ID_MASK) == 0;
    }

    /**
     * Index of the item inside of data page.
     *
     * @param link Page link.
     * @return Offset in 8-byte words.
     */
    public static int itemId(long link) {
        return (int) ((link >> ROTATION_ID_OFFSET) & OFFSET_MASK);
    }

    /**
     * Tag of pageId.
     *
     * @param link Page link.
     * @return tag - item id + flags
     */
    public static int tag(long link) {
        return (int) ((link >> (PAGE_IDX_SIZE + PART_ID_SIZE)) & TAG_MASK);
    }

    /**
     * Extracts flags byte from the page ID.
     *
     * @param pageId Page ID.
     * @return Flag.
     */
    public static byte flag(long pageId) {
        return (byte) ((pageId >>> (PART_ID_SIZE + PAGE_IDX_SIZE)) & FLAG_MASK);
    }

    /**
     * Extracts partition ID from the page ID.
     *
     * @param pageId Page ID.
     * @return Partition.
     */
    public static int partitionId(long pageId) {
        return (int) ((pageId >>> PAGE_IDX_SIZE) & PART_ID_MASK);
    }

    /**
     * Returns the Rotation ID of a page identified by the given ID.
     */
    public static long rotationId(long pageId) {
        return pageId >>> ROTATION_ID_OFFSET;
    }

    /**
     * Rotates rotated ID part of the page ID.
     *
     * @param pageId Page ID.
     * @return New page ID.
     */
    public static long rotatePageId(long pageId) {
        long updatedRotationId = rotationId(pageId) + 1;

        if (updatedRotationId > MAX_ITEMID_NUM) {
            updatedRotationId = 1; // We always want non-zero updatedRotationId
        }

        return (pageId & PAGE_ID_MASK) | (updatedRotationId << ROTATION_ID_OFFSET);
    }

    /**
     * Masks partition ID from full page ID. Effectively the same as {@code changePartitionId(pageId, 0)}.
     *
     * @param pageId Page ID to mask partition ID from.
     * @see #changePartitionId(long, int)
     */
    public static long maskPartitionId(long pageId) {
        return pageId & ~((-1L << PAGE_IDX_SIZE) & (~(-1L << PAGE_IDX_SIZE + PART_ID_SIZE)));
    }

    /**
     * Change page type.
     *
     * @param pageId Old page ID.
     * @param type   New page type.
     * @return Changed page ID.
     */
    public static long changeType(long pageId, byte type) {
        return pageId(partitionId(pageId), type, pageIndex(pageId));
    }

    /**
     * Returns convenient human-readable page ID representation.
     *
     * @param pageId Page id.
     */
    public static String toDetailString(long pageId) {
        return "pageId=" + pageId
                + "(offset=" + itemId(pageId)
                + ", flags=" + Integer.toBinaryString(flag(pageId))
                + ", partitionId=" + partitionId(pageId)
                + ", index=" + pageIndex(pageId)
                + ")";
    }

    /**
     * Replaces partition ID in the page ID.
     *
     * @param pageId      Page ID.
     * @param partitionId Partition ID.
     */
    public static long changePartitionId(long pageId, int partitionId) {
        byte flag = flag(pageId);
        int pageIdx = pageIndex(pageId);

        return pageId(partitionId, flag, pageIdx);
    }
}
