variable "name" {
  description = "Name of the Cognito user pool and hosted UI domain prefix."
  type        = string
}

variable "callback_urls" {
  description = "OAuth2 callback URLs allowed for the Cognito app client."
  type        = list(string)
}

variable "logout_urls" {
  description = "OAuth2 logout URLs allowed for the Cognito app client."
  type        = list(string)
}
