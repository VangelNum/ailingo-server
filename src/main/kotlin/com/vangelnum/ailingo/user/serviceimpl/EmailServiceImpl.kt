package com.vangelnum.ailingo.user.serviceimpl

import com.vangelnum.ailingo.user.service.EmailService
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class EmailServiceImpl(
    private val mailSender: JavaMailSender,
    @Value("\${MAIL_USERNAME}") private val mailUsername: String
): EmailService {

    override fun sendVerificationEmail(to: String, verificationCode: String) {
        val message = SimpleMailMessage()
        message.setTo(to)
        message.subject = "Подтверждение регистрации на Ailingo"
        message.text = """
                Здравствуйте!
    
                Благодарим вас за регистрацию в Ailingo.
                Для подтверждения вашего email, пожалуйста, введите следующий код подтверждения:
    
                $verificationCode
    
                Если возникли проблемы с регистрацией, напишите на почту vangelnum@gmail.com
    
                С уважением,
                Команда Ailigno.
            
            """.trimIndent()
        message.from = mailUsername

        mailSender.send(message)
    }
}