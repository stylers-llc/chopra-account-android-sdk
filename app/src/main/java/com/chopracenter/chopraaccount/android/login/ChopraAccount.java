package com.chopracenter.chopraaccount.android.login;

import org.json.JSONException;
import org.json.JSONObject;

public class ChopraAccount {

    private String id;
    private String firstName;
    private String lastName;
    private String gender;
    private String email;
    private String birthDate;
    private String profileImage;

    public ChopraAccount(JSONObject object) {
        try {
            id = object.getString("id");
            firstName = object.getString("first_name");
            lastName = object.getString("last_name");
            email = object.getString("email");
            gender = object.getString("gender");
            birthDate = object.getString("birthdate");
            profileImage = object.getString("profile_image");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public String getGender() {
        return gender;
    }

    public String getEmail() {
        return email;
    }

    public String getId() {
        return id;
    }

    public String getBirthDate() {
        return birthDate;
    }
}