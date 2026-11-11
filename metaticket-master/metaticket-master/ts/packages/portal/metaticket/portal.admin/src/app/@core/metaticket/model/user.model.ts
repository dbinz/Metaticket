export interface UserModel {
    username: string;
    picture: string;
}

export interface ContactModel {
    user: UserModel;
    type: string;
}

export interface RecentUserModel extends ContactModel {
    time: number;
}
