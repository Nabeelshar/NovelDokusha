from fastapi import APIRouter, Body, Depends, Form, Security

from ..context import ServerContext
from ..models.user import (CreateRequest, ForgotPasswordRequest, LoginRequest,
                           LoginResponse, NameUpdateRequest,
                           PasswordUpdateRequest, ResetPasswordRequest,
                           SignupRequest, TokenResponse, UpdateRequest, User)
from ..security import ensure_user

# The root router
router = APIRouter()


@router.post("/login", summary="Login with username or email and password")
def login(
    ctx: ServerContext = Depends(),
    credentials: LoginRequest = Body(
        default=...,
        description='The login credentials',
    ),
) -> LoginResponse:
    user = ctx.users.verify(credentials)
    token = ctx.users.generate_token(user)
    is_verified = ctx.users.is_verified(user.email)
    return LoginResponse(
        token=token,
        user=user,
        is_verified=is_verified,
    )


@router.post('/signup', summary='Signup as a new user')
def signup(
    ctx: ServerContext = Depends(),
    body: SignupRequest = Body(
        default=...,
        description='The signup request',
    ),
) -> LoginResponse:
    request = CreateRequest(
        password=body.password,
        email=body.email,
        name=body.name,
    )
    user = ctx.users.create(request)
    token = ctx.users.generate_token(user)
    is_verified = ctx.users.is_verified(user.email)
    return LoginResponse(
        token=token,
        user=user,
        is_verified=is_verified,
    )


@router.get('/me', summary='Get current user details')
def me(
    user: User = Security(ensure_user),
) -> User:
    return user


@router.put('/me/name', summary='Update current user name')
def self_name_update(
    ctx: ServerContext = Depends(),
    user: User = Security(ensure_user),
    body: NameUpdateRequest = Body(description='The update request'),
) -> bool:
    request = UpdateRequest(name=body.name)
    return ctx.users.update(user.id, request)


@router.put('/me/password', summary='Update current user password')
def self_password_update(
    ctx: ServerContext = Depends(),
    user: User = Security(ensure_user),
    body: PasswordUpdateRequest = Body(description='The update request'),
) -> bool:
    return ctx.users.change_password(user, body)


@router.post('/send-password-reset-link', summary='Send reset password link to email')
def send_password_reset_link(
    ctx: ServerContext = Depends(),
    body: ForgotPasswordRequest = Body(description='The request body'),
) -> bool:
    return ctx.users.send_password_reset_link(body.email)


@router.post('/reset-password-with-token', summary='Verify token and change password')
def reset_password_with_token(
    ctx: ServerContext = Depends(),
    user: User = Security(ensure_user),
    body: ResetPasswordRequest = Body(description='The request body'),
) -> bool:
    request = UpdateRequest(password=body.password)
    updated = ctx.users.update(user.id, request)
    is_verified = ctx.users.set_verified(user.email)
    return updated and is_verified


@router.post('/me/send-otp', summary='Send OTP to current user email for verification')
def send_otp(
    ctx: ServerContext = Depends(),
    user: User = Security(ensure_user),
) -> TokenResponse:
    token = ctx.users.send_otp(user.email)
    return TokenResponse(token=token)


@router.post('/me/verify-otp', summary='Get if current user email is verified')
def verify_otp(
    otp: str = Form(),
    token: str = Form(),
    ctx: ServerContext = Depends(),
) -> bool:
    return ctx.users.verify_otp(token, otp)
