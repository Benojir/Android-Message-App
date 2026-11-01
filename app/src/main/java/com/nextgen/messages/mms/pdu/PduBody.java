package com.nextgen.messages.mms.pdu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds ordered PduParts composing a multipart MMS message.
 */
public class PduBody {

    private final ArrayList<PduPart> mParts = new ArrayList<>();
    private final Map<String, PduPart> mPartMapByContentId = new HashMap<>();
    private final Map<String, PduPart> mPartMapByName = new HashMap<>();
    private final Map<String, PduPart> mPartMapByFileName = new HashMap<>();

    public PduBody() {}

    private void putPartToMaps(@NonNull PduPart part) {
        byte[] contentId = part.getContentId();
        if (contentId != null) {
            mPartMapByContentId.put(new String(contentId), part);
        }
        byte[] name = part.getName();
        if (name != null) {
            mPartMapByName.put(new String(name), part);
        }
        byte[] filename = part.getFilename();
        if (filename != null) {
            mPartMapByFileName.put(new String(filename), part);
        }
    }

    public boolean addPart(@NonNull PduPart part) {
        if (part == null) {
            throw new NullPointerException();
        }
        putPartToMaps(part);
        return mParts.add(part);
    }

    public void addPart(int index, @NonNull PduPart part) {
        if (part == null) {
            throw new NullPointerException();
        }
        putPartToMaps(part);
        mParts.add(index, part);
    }

    @NonNull
    public PduPart getPart(int index) {
        return mParts.get(index);
    }

    public int getPartsNum() {
        return mParts.size();
    }

    @Nullable
    public PduPart getPartByContentId(@NonNull String contentId) {
        return mPartMapByContentId.get(contentId);
    }

    @Nullable
    public PduPart getPartByName(@NonNull String name) {
        return mPartMapByName.get(name);
    }

    @Nullable
    public PduPart getPartByFileName(@NonNull String fileName) {
        return mPartMapByFileName.get(fileName);
    }

    public void removeAll() {
        mParts.clear();
        mPartMapByContentId.clear();
        mPartMapByName.clear();
        mPartMapByFileName.clear();
    }
}
