package com.hairsalonbookingapp.hairsalon.exception;

public class AccountBlockedException extends RuntimeException{
    public AccountBlockedException(String message){
        super(message);
    }
}
