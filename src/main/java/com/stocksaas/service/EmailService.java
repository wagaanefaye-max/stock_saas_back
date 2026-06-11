package com.stocksaas.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service pour l'envoi d'emails
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    
    private final JavaMailSender mailSender;
    
    @Value("${spring.mail.username}")
    private String fromEmail;
    
    /**
     * Envoie un email de notification de connexion
     */
    public void sendLoginNotification(String toEmail, String userName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Notification de connexion - Stock SaaS");
            message.setText(buildLoginEmailContent(userName));
            
            mailSender.send(message);
            log.info("Email de notification de connexion envoyé à: {}", toEmail);
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de l'email de notification de connexion à {}: {}", toEmail, e.getMessage());
            // Ne pas faire échouer la connexion si l'email échoue
        }
    }
    
    @Value("${app.base-url:http://localhost:4200}")
    private String appBaseUrl;

    /**
     * Envoie un email avec lien de réinitialisation (définir le mot de passe, valide 2 jours)
     */
    public void sendAccountVerificationEmail(String toEmail, String userName, String companyName, String verificationToken) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Définissez votre mot de passe - Stock SaaS - " + companyName);
            
            String base = appBaseUrl != null && !appBaseUrl.isBlank() ? appBaseUrl.trim() : "http://localhost:4200";
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            String verificationUrl = base + "/verify-account?token=" + verificationToken;
            message.setText(buildVerificationEmailContent(userName, companyName, verificationUrl));
            
            mailSender.send(message);
            log.info("Email de validation de compte envoyé à: {}", toEmail);
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de l'email de validation à {}: {}", toEmail, e.getMessage());
            // Ne pas faire échouer l'inscription si l'email échoue
        }
    }
    
    /**
     * Envoie un email de confirmation d'activation de compte
     */
    public void sendAccountActivatedEmail(String toEmail, String userName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Votre compte Stock SaaS est activé");
            message.setText(buildAccountActivatedEmailContent(userName));
            
            mailSender.send(message);
            log.info("Email de confirmation d'activation envoyé à: {}", toEmail);
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de l'email de confirmation à {}: {}", toEmail, e.getMessage());
        }
    }
    
    /**
     * Construit le contenu de l'email de notification de connexion
     */
    private String buildLoginEmailContent(String userName) {
        return "Bonjour " + userName + ",\n\n" +
               "Une connexion à votre compte Stock SaaS a été détectée.\n\n" +
               "Si vous n'êtes pas à l'origine de cette connexion, veuillez contacter immédiatement le support.\n\n" +
               "Cordialement,\n" +
               "L'équipe Stock SaaS";
    }
    
    /**
     * Construit le contenu de l'email de validation de compte
     */
    private String buildVerificationEmailContent(String userName, String companyName, String verificationUrl) {
        return "Bonjour " + userName + ",\n\n" +
               "Bienvenue sur Stock SaaS !\n\n" +
               "Votre compte a été créé pour l'entreprise : " + companyName + ".\n\n" +
               "Pour activer votre compte, cliquez sur le lien ci-dessous pour définir votre mot de passe :\n\n" +
               verificationUrl + "\n\n" +
               "Ce lien de réinitialisation est valable 2 jours.\n\n" +
               "Si vous n'avez pas créé de compte, veuillez ignorer cet email.\n\n" +
               "Cordialement,\n" +
               "L'équipe Stock SaaS";
    }
    
    /**
     * Construit le contenu de l'email de confirmation d'activation
     */
    private String buildAccountActivatedEmailContent(String userName) {
        return "Bonjour " + userName + ",\n\n" +
               "Votre compte Stock SaaS a été activé avec succès !\n\n" +
               "Vous pouvez maintenant vous connecter à votre compte et commencer à gérer votre inventaire.\n\n" +
               "Cordialement,\n" +
               "L'équipe Stock SaaS";
    }

    /**
     * Envoie un email au client avec un lien pour télécharger la facture (facture marquée payée).
     */
    public void sendInvoicePaidWithDownloadLink(String toEmail, String clientName, String invoiceNumber, String downloadUrl) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Email client vide, envoi du lien de téléchargement de facture ignoré pour {}", invoiceNumber);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail.trim());
            message.setSubject("Votre facture " + (invoiceNumber != null ? invoiceNumber : "") + " - Stock SaaS");
            message.setText(buildInvoicePaidEmailContent(clientName, invoiceNumber, downloadUrl));

            mailSender.send(message);
            log.info("Email avec lien de téléchargement de facture envoyé à: {}", toEmail);
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de l'email avec lien de téléchargement à {}: {}", toEmail, e.getMessage());
            // Ne pas faire échouer la création de la facture si l'email échoue
        }
    }

    /**
     * Rappel avant expiration de l'essai ou de l'abonnement (7 jours ou 1 jour).
     */
    public void sendSubscriptionExpiryReminder(
            String toEmail,
            String recipientName,
            String companyName,
            String subscriptionKindLabel,
            LocalDateTime periodEndAt,
            long daysBeforeEnd,
            String subscriptionsUrl
    ) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail.trim());
            message.setSubject(buildSubscriptionReminderSubject(companyName, daysBeforeEnd));
            message.setText(buildSubscriptionReminderContent(
                    recipientName,
                    companyName,
                    subscriptionKindLabel,
                    periodEndAt,
                    daysBeforeEnd,
                    subscriptionsUrl
            ));
            mailSender.send(message);
            log.info("Rappel abonnement J-{} envoyé à {} pour {}", daysBeforeEnd, toEmail, companyName);
        } catch (Exception e) {
            log.error("Erreur envoi rappel abonnement à {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildSubscriptionReminderSubject(String companyName, long daysBeforeEnd) {
        if (daysBeforeEnd <= 1) {
            return "Stock SaaS — Votre abonnement se termine demain (" + companyName + ")";
        }
        return "Stock SaaS — Votre abonnement se termine dans 7 jours (" + companyName + ")";
    }

    private String buildSubscriptionReminderContent(
            String recipientName,
            String companyName,
            String subscriptionKindLabel,
            java.time.LocalDateTime periodEndAt,
            long daysBeforeEnd,
            String subscriptionsUrl
    ) {
        String endFormatted = periodEndAt.format(
                java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH:mm", java.util.Locale.FRENCH));
        String delayLabel = daysBeforeEnd <= 1
                ? "demain"
                : "dans 7 jours";

        return "Bonjour " + (recipientName != null && !recipientName.isBlank() ? recipientName : "Administrateur") + ",\n\n"
                + "Ceci est un rappel concernant l'espace entreprise " + companyName + " sur Stock SaaS.\n\n"
                + "Votre " + subscriptionKindLabel + " se termine " + delayLabel + ", le " + endFormatted + ".\n\n"
                + "Après cette date, l'accès passera en lecture seule : vous pourrez consulter vos données "
                + "mais plus effectuer de modifications (factures, stock, mouvements, etc.).\n\n"
                + "Pour prolonger votre abonnement, rendez-vous sur la page Abonnement et soumettez "
                + "une nouvelle demande avec votre justificatif de paiement (Wave ou Orange Money) :\n\n"
                + subscriptionsUrl + "\n\n"
                + "Cordialement,\n"
                + "L'équipe Stock SaaS";
    }

    private String buildInvoicePaidEmailContent(String clientName, String invoiceNumber, String downloadUrl) {
        return "Bonjour " + (clientName != null ? clientName : "Client") + ",\n\n" +
               "Votre facture " + (invoiceNumber != null ? invoiceNumber : "") + " a été enregistrée comme payée.\n\n" +
               "Vous pouvez télécharger votre facture au format PDF en cliquant sur le lien suivant :\n\n" +
               downloadUrl + "\n\n" +
               "Ce lien est valable 30 jours.\n\n" +
               "Cordialement,\n" +
               "L'équipe Stock SaaS";
    }

    /**
     * Envoie la facture d'abonnement en pièce jointe PDF après validation par le super admin.
     */
    public void sendSubscriptionApprovedWithInvoice(
            String toEmail,
            String recipientName,
            String companyName,
            String invoiceNumber,
            byte[] pdfBytes
    ) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Email souscripteur vide, envoi de la facture d'abonnement {} ignoré", invoiceNumber);
            return;
        }
        if (pdfBytes == null || pdfBytes.length == 0) {
            log.warn("PDF facture abonnement {} vide, envoi ignoré pour {}", invoiceNumber, toEmail);
            return;
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail.trim());
            helper.setSubject("Votre facture d'abonnement " + invoiceNumber + " - Stock SaaS");
            helper.setText(buildSubscriptionApprovedEmailContent(recipientName, companyName, invoiceNumber), false);

            String filename = "facture-abonnement-" + invoiceNumber + ".pdf";
            helper.addAttachment(filename, new ByteArrayResource(pdfBytes), "application/pdf");

            mailSender.send(mimeMessage);
            log.info("Facture d'abonnement {} envoyée par email à {}", invoiceNumber, toEmail);
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de la facture d'abonnement {} à {}: {}",
                    invoiceNumber, toEmail, e.getMessage());
        }
    }

    private String buildSubscriptionApprovedEmailContent(
            String recipientName,
            String companyName,
            String invoiceNumber
    ) {
        return "Bonjour " + (recipientName != null && !recipientName.isBlank() ? recipientName : "Administrateur") + ",\n\n"
                + "Votre demande d'abonnement pour l'entreprise " + companyName + " a été validée.\n\n"
                + "Vous trouverez en pièce jointe la facture " + invoiceNumber + " au format PDF.\n\n"
                + "Votre abonnement est désormais actif. Vous pouvez vous connecter à Stock SaaS "
                + "et profiter de toutes les fonctionnalités de gestion de stock.\n\n"
                + "Cordialement,\n"
                + "L'équipe Stock SaaS";
    }
}
