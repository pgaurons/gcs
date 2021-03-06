/*
 * Copyright ©1998-2020 by Richard A. Wilkes. All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, version 2.0. If a copy of the MPL was not distributed with
 * this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, version 2.0.
 */

package com.trollworks.gcs.feature;

import com.trollworks.gcs.criteria.StringCompareType;
import com.trollworks.gcs.criteria.StringCriteria;
import com.trollworks.gcs.skill.Skill;
import com.trollworks.gcs.ui.widget.outline.ListRow;
import com.trollworks.gcs.utility.xml.XMLReader;
import com.trollworks.gcs.utility.xml.XMLWriter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/** A skill bonus. */
public class SkillBonus extends Bonus {
    /** The XML tag. */
    public static final  String         TAG_ROOT           = "skill_bonus";
    private static final String         TAG_NAME           = "name";
    private static final String         TAG_SPECIALIZATION = "specialization";
    private static final String         TAG_CATEGORY       = "category";
    private static final String         TAG_PARENT_ONLY    = "parent_only";
    private              StringCriteria mNameCriteria;
    private              StringCriteria mSpecializationCriteria;
    private              StringCriteria mCategoryCriteria;
    private              boolean        mApplyToParentOnly;

    /** Creates a new skill bonus. */
    public SkillBonus() {
        super(1);
        mNameCriteria = new StringCriteria(StringCompareType.IS, "");
        mSpecializationCriteria = new StringCriteria(StringCompareType.IS_ANYTHING, "");
        mCategoryCriteria = new StringCriteria(StringCompareType.IS_ANYTHING, "");
    }

    /**
     * Loads a {@link SkillBonus}.
     *
     * @param reader The XML reader to use.
     */
    public SkillBonus(XMLReader reader) throws IOException {
        this();
        load(reader);
    }

    /**
     * Creates a clone of the specified bonus.
     *
     * @param other The bonus to clone.
     */
    public SkillBonus(SkillBonus other) {
        super(other);
        mNameCriteria = new StringCriteria(other.mNameCriteria);
        mSpecializationCriteria = new StringCriteria(other.mSpecializationCriteria);
        mCategoryCriteria = new StringCriteria(other.mCategoryCriteria);
        mApplyToParentOnly = other.mApplyToParentOnly;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof SkillBonus && super.equals(obj)) {
            SkillBonus sb = (SkillBonus) obj;
            if (mNameCriteria.equals(sb.mNameCriteria)) {
                return mApplyToParentOnly == sb.mApplyToParentOnly && mNameCriteria.equals(sb.mNameCriteria) && mSpecializationCriteria.equals(sb.mSpecializationCriteria) && mCategoryCriteria.equals(sb.mCategoryCriteria);
            }
        }
        return false;
    }

    @Override
    public Feature cloneFeature() {
        return new SkillBonus(this);
    }

    @Override
    public String getXMLTag() {
        return TAG_ROOT;
    }

    @Override
    public String getKey() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(Skill.ID_NAME);
        if (mApplyToParentOnly) {
            buffer.append("\u0001");
        } else if (mNameCriteria.isTypeIs() && mSpecializationCriteria.isTypeAnything() && mCategoryCriteria.isTypeAnything()) {
            buffer.append('/');
            buffer.append(mNameCriteria.getQualifier());
        } else {
            buffer.append("*");
        }
        return buffer.toString();
    }

    public boolean matchesCategories(Set<String> categories) {
        return matchesCategories(mCategoryCriteria, categories);
    }

    @Override
    protected void loadSelf(XMLReader reader) throws IOException {
        String name = reader.getName();
        if (TAG_NAME.equals(name)) {
            mNameCriteria.load(reader);
        } else if (TAG_SPECIALIZATION.equals(name)) {
            mSpecializationCriteria.load(reader);
        } else if (TAG_CATEGORY.equals(name)) {
            mCategoryCriteria.load(reader);
        } else if (TAG_PARENT_ONLY.equals(reader.getName())) {
            mApplyToParentOnly = reader.readBoolean();
        } else {
            super.loadSelf(reader);
        }
    }

    /**
     * Saves the bonus.
     *
     * @param out The XML writer to use.
     */
    @Override
    public void save(XMLWriter out) {
        out.startSimpleTagEOL(TAG_ROOT);
        if (mApplyToParentOnly) {
            out.simpleTag(TAG_PARENT_ONLY, true);
        } else {
            mNameCriteria.save(out, TAG_NAME);
            mSpecializationCriteria.save(out, TAG_SPECIALIZATION);
            mCategoryCriteria.save(out, TAG_CATEGORY);
        }
        saveBase(out);
        out.endTagEOL(TAG_ROOT, true);
    }

    public boolean applyToParentOnly() {
        return mApplyToParentOnly;
    }

    public boolean setApplyToParentOnly(boolean apply) {
        if (mApplyToParentOnly != apply) {
            mApplyToParentOnly = apply;
            return true;
        }
        return false;
    }

    /** @return The name criteria. */
    public StringCriteria getNameCriteria() {
        return mNameCriteria;
    }

    /** @return The name criteria. */
    public StringCriteria getSpecializationCriteria() {
        return mSpecializationCriteria;
    }

    /** @return The category criteria. */
    public StringCriteria getCategoryCriteria() {
        return mCategoryCriteria;
    }

    @Override
    public void fillWithNameableKeys(Set<String> set) {
        if (!mApplyToParentOnly) {
            ListRow.extractNameables(set, mNameCriteria.getQualifier());
            ListRow.extractNameables(set, mSpecializationCriteria.getQualifier());
            ListRow.extractNameables(set, mCategoryCriteria.getQualifier());
        }
    }

    @Override
    public void applyNameableKeys(Map<String, String> map) {
        if (!mApplyToParentOnly) {
            mNameCriteria.setQualifier(ListRow.nameNameables(map, mNameCriteria.getQualifier()));
            mSpecializationCriteria.setQualifier(ListRow.nameNameables(map, mSpecializationCriteria.getQualifier()));
            mCategoryCriteria.setQualifier(ListRow.nameNameables(map, mCategoryCriteria.getQualifier()));
        }
    }
}
