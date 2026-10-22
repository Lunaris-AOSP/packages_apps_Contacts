/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.contacts.util;

import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Parcel;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.test.AndroidTestCase;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

import androidx.test.filters.SmallTest;

import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.account.DeviceLocalAccountType;

import java.util.ArrayList;

@SmallTest
public class DeviceLocalAccountTypeFactoryTest extends AndroidTestCase {
    private static final String SIM_ACCOUNT_TYPE = "com.example.sim";
    private static final String OTHER_ACCOUNT_TYPE = "com.example.other";

    private MockContentResolver mResolver;
    private DeviceLocalAccountTypeFactory mFactory;
    private RuntimeException mProviderFailure;
    private Bundle mResponse;
    private int mProviderCalls;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mProviderFailure = null;
        mResponse = new Bundle();
        mProviderCalls = 0;
        mResolver = new MockContentResolver(getContext());
        mResolver.addProvider(ContactsContract.AUTHORITY, new MockContentProvider(getContext()) {
            @Override
            public Bundle call(String method, String arg, Bundle extras) {
                assertEquals("querySimAccounts", method);
                mProviderCalls++;
                if (mProviderFailure != null) {
                    throw mProviderFailure;
                }
                return mResponse;
            }
        });
        Context context = new ContextWrapper(getContext()) {
            @Override
            public ContentResolver getContentResolver() {
                return mResolver;
            }
        };
        mFactory = new DeviceLocalAccountTypeFactory.Default(context);
    }

    public void testNullAccountDoesNotQueryProvider() {
        mProviderFailure = new RuntimeException(new DeadObjectException());

        assertEquals(DeviceLocalAccountTypeFactory.TYPE_DEVICE, mFactory.classifyAccount(null));
        assertEquals(0, mProviderCalls);
    }

    public void testLocalAccountDoesNotQueryProvider() {
        mProviderFailure = new RuntimeException(new DeadObjectException());

        assertEquals(DeviceLocalAccountTypeFactory.TYPE_DEVICE,
                mFactory.classifyAccount(AccountWithDataSet.getLocalAccount(getContext()).type));
        assertEquals(0, mProviderCalls);
    }

    public void testDeviceAccountTypeDoesNotQueryProvider() {
        mProviderFailure = new RuntimeException(new DeadObjectException());

        assertTrue(mFactory.getAccountType(null) instanceof DeviceLocalAccountType);
        assertEquals(0, mProviderCalls);
    }

    public void testProviderDeathDoesNotCrashClassification() {
        mProviderFailure = new RuntimeException(new DeadObjectException());

        assertEquals(DeviceLocalAccountTypeFactory.TYPE_OTHER,
                mFactory.classifyAccount(OTHER_ACCOUNT_TYPE));
        assertEquals(1, mProviderCalls);
    }

    public void testProviderDeathReturnsEmptySimAccounts() {
        mProviderFailure = new RuntimeException(new DeadObjectException());

        assertTrue(SimContactsUtil.getSimAccounts(mResolver).isEmpty());
        assertEquals(1, mProviderCalls);
    }

    public void testRemoteFailureReturnsEmptySimAccounts() {
        mProviderFailure = new RuntimeException(new RemoteException());

        assertTrue(SimContactsUtil.getSimAccounts(mResolver).isEmpty());
    }

    public void testUnrelatedRuntimeFailureIsPropagated() {
        mProviderFailure = new IllegalStateException("Unexpected provider failure");

        try {
            SimContactsUtil.getSimAccounts(mResolver);
            fail("Expected the unrelated exception to propagate");
        } catch (RuntimeException e) {
            assertSame(mProviderFailure, e);
        }
    }

    public void testEmptySimAccounts() {
        assertTrue(SimContactsUtil.getSimAccounts(mResolver).isEmpty());
        assertEquals(DeviceLocalAccountTypeFactory.TYPE_OTHER,
                mFactory.classifyAccount(OTHER_ACCOUNT_TYPE));
    }

    public void testSimAccountIsClassified() {
        setSimAccountResponse();

        assertEquals(DeviceLocalAccountTypeFactory.TYPE_SIM,
                mFactory.classifyAccount(SIM_ACCOUNT_TYPE));
        assertEquals(DeviceLocalAccountTypeFactory.TYPE_OTHER,
                mFactory.classifyAccount(OTHER_ACCOUNT_TYPE));
    }

    public void testSimAccountIsRecognizedAfterProviderRecovers() {
        mProviderFailure = new RuntimeException(new DeadObjectException());
        assertEquals(DeviceLocalAccountTypeFactory.TYPE_OTHER,
                mFactory.classifyAccount(SIM_ACCOUNT_TYPE));

        mProviderFailure = null;
        setSimAccountResponse();

        assertEquals(DeviceLocalAccountTypeFactory.TYPE_SIM,
                mFactory.classifyAccount(SIM_ACCOUNT_TYPE));
        assertEquals(2, mProviderCalls);
    }

    private void setSimAccountResponse() {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeString("SIM 1");
            parcel.writeString(SIM_ACCOUNT_TYPE);
            parcel.writeInt(0);
            parcel.writeInt(ContactsContract.SimAccount.ADN_EF_TYPE);
            parcel.setDataPosition(0);
            ArrayList<ContactsContract.SimAccount> accounts = new ArrayList<>();
            accounts.add(ContactsContract.SimAccount.CREATOR.createFromParcel(parcel));
            mResponse.putParcelableArrayList("key_sim_accounts", accounts);
        } finally {
            parcel.recycle();
        }
    }
}
