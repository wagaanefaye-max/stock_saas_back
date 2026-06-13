package com.stocksaas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service pour l'envoi d'emails (via file d'attente avec relance automatique).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final EmailOutboxService emailOutboxService;

    @Value("${app.base-url:http://localhost:4200}")
    private String appBaseUrl;

    /**
     * Envoie un email de notification de connexion (asynchrone — hors chemin critique du login).
     */
    @org.springframework.scheduling.annotation.Async
    public void sendLoginNotification(String toEmail, String userName) {
        if (isBlank(toEmail)) {
            return;
        }
        emailOutboxService.enqueueTextEmail(
                toEmail.trim(),
                "Notification de connexion - Stock SaaS",
                buildLoginEmailContent(userName),
                "LOGIN_NOTIFICATION"
        );
    }

    /**
     * Envoie un email avec lien de réinitialisation (définir le mot de passe, valide 2 jours)
     */
    public void sendAccountVerificationEmail(String toEmail, String userName, String companyName, String verificationToken) {
        if (isBlank(toEmail)) {
            return;
        }
        String base = appBaseUrl != null && !appBaseUrl.isBlank() ? appBaseUrl.trim() : "http://localhost:4200";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String verificationUrl = base + "/verify-account?token=" + verificationToken;
        emailOutboxService.enqueueTextEmail(
                toEmail.trim(),
                "Définissez votre mot de passe - Stock SaaS - " + companyName,
                buildVerificationEmailContent(userName, companyName, verificationUrl),
                "ACCOUNT_VERIFICATION"
        );
    }

    /**
     * Envoie un email de confirmation d'activation de compte
     */
    public void sendAccountActivatedEmail(String toEmail, String userName) {
        if (isBlank(toEmail)) {
            return;
        }
        emailOutboxService.enqueueTextEmail(
                toEmail.trim(),
                "Votre compte Stock SaaS est activé",
                buildAccountActivatedEmailContent(userName),
                "ACCOUNT_ACTIVATED"
        );
    }

    /**
     * Envoie un email au client avec un lien pour télécharger la facture (facture marquée payée).
     */
    public void sendInvoicePaidWithDownloadLink(String toEmail, String clientName, String invoiceNumber, String downloadUrl) {
        if (isBlank(toEmail)) {
            log.warn("Email client vide, envoi du lien de téléchargement de facture ignoré pour {}", invoiceNumber);
            return;
        }
        emailOutboxService.enqueueTextEmail(
                toEmail.trim(),
                "Votre facture " + (invoiceNumber != null ? invoiceNumber : "") + " - Stock SaaS",
                buildInvoicePaidEmailContent(clientName, invoiceNumber, downloadUrl),
                "INVOICE_PAID"
        );
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
        if (isBlank(toEmail)) {
            return;
        }
        emailOutboxService.enqueueTextEmail(
                toEmail.trim(),
                buildSubscriptionReminderSubject(companyName, daysBeforeEnd),
                buildSubscriptionReminderContent(
                        recipientName,
                        companyName,
                        subscriptionKindLabel,
                        periodEndAt,
                        daysBeforeEnd,
                        subscriptionsUrl
                ),
                "SUBSCRIPTION_REMINDER"
        );
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
        if (isBlank(toEmail)) {
            log.warn("Email souscripteur vide, envoi de la facture d'abonnement {} ignoré", invoiceNumber);
            return;
        }
        if (pdfBytes == null || pdfBytes.length == 0) {
            log.warn("PDF facture abonnement {} vide, envoi ignoré pour {}", invoiceNumber, toEmail);
            return;
        }
        String filename = "facture-abonnement-" + invoiceNumber + ".pdf";
        emailOutboxService.enqueueWithAttachment(
                toEmail.trim(),
                "Votre facture d'abonnement " + invoiceNumber + " - Stock SaaS",
                buildSubscriptionApprovedEmailContent(recipientName, companyName, invoiceNumber),
                "SUBSCRIPTION_APPROVED",
                filename,
                "application/pdf",
                pdfBytes
        );
    }

    /**
     * Notifie un super admin qu'une nouvelle demande de souscription a été soumise.
     */
    public void notifySuperAdminNewSubscriptionRequest(
            String superAdminEmail,
            String superAdminName,
            String companyName,
            String requesterName,
            String requesterEmail,
            String planLabel,
            String durationLabel,
            double amountPaid,
            String paymentProviderLabel,
            Long requestId
    ) {
        if (isBlank(superAdminEmail)) {
            return;
        }
        emailOutboxService.enqueueTextEmail(
                superAdminEmail.trim(),
                "Nouvelle demande de souscription — " + companyName + " - Stock SaaS",
                buildSuperAdminNewSubscriptionContent(
                        superAdminName,
                        companyName,
                        requesterName,
                        requesterEmail,
                        planLabel,
                        durationLabel,
                        amountPaid,
                        paymentProviderLabel,
                        requestId
                ),
                "SUBSCRIPTION_REQUEST_SUPER_ADMIN"
        );
    }

    /**
     * Notifie le demandeur lorsqu'une souscription est refusée par le super admin.
     */
    public void sendSubscriptionRejected(
            String toEmail,
            String recipientName,
            String companyName,
            String rejectionReason
    ) {
        if (isBlank(toEmail)) {
            log.warn("Email souscripteur vide, notification de rejet ignorée pour {}", companyName);
            return;
        }
        emailOutboxService.enqueueTextEmail(
                toEmail.trim(),
                "Demande d'abonnement refusée - " + companyName + " - Stock SaaS",
                buildSubscriptionRejectedEmailContent(
                        recipientName,
                        companyName,
                        rejectionReason,
                        buildSubscriptionsUrl()
                ),
                "SUBSCRIPTION_REJECTED"
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String buildLoginEmailContent(String userName) {
        return "Bonjour " + userName + ",\n\n" +
               "Une connexion à votre compte Stock SaaS a été détectée.\n\n" +
               "Si vous n'êtes pas à l'origine de cette connexion, veuillez contacter immédiatement le support.\n\n" +
               "Cordialement,\n" +
               "L'équipe Stock SaaS";
    }

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

    private String buildAccountActivatedEmailContent(String userName) {
        return "Bonjour " + userName + ",\n\n" +
               "Votre compte Stock SaaS a été activé avec succès !\n\n" +
               "Vous pouvez maintenant vous connecter à votre compte et commencer à gérer votre inventaire.\n\n" +
               "Cordialement,\n" +
               "L'équipe Stock SaaS";
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
            LocalDateTime periodEndAt,
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
                + "Cordialement,\n" +
                "L'équipe Stock SaaS";
    }

    private String buildSubscriptionsUrl() {
        String base = appBaseUrl != null && !appBaseUrl.isBlank() ? appBaseUrl.trim() : "http://localhost:4200";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/company-admin/subscriptions";
    }

    private String buildSuperAdminSubscriptionRequestsUrl() {
        String base = appBaseUrl != null && !appBaseUrl.isBlank() ? appBaseUrl.trim() : "http://localhost:4200";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/super-admin/subscription-requests";
    }

    private String buildSuperAdminNewSubscriptionContent(
            String superAdminName,
            String companyName,
            String requesterName,
            String requesterEmail,
            String planLabel,
            String durationLabel,
            double amountPaid,
            String paymentProviderLabel,
            Long requestId
    ) {
        String amountFormatted = String.format("%,.0f", amountPaid).replace(',', ' ');
        String requester = (requesterName != null && !requesterName.isBlank() ? requesterName : "Utilisateur")
                + (requesterEmail != null && !requesterEmail.isBlank() ? " (" + requesterEmail + ")" : "");

        return "Bonjour " + (superAdminName != null && !superAdminName.isBlank() ? superAdminName : "Super administrateur") + ",\n\n"
                + "Une nouvelle demande de souscription vient d'être soumise sur Stock SaaS.\n\n"
                + "Entreprise : " + companyName + "\n"
                + "Demandeur : " + requester + "\n"
                + "Plan : " + (planLabel != null ? planLabel : "Standard") + "\n"
                + "Durée : " + (durationLabel != null ? durationLabel : "") + "\n"
                + "Montant : " + amountFormatted + " FCFA\n"
                + "Paiement : " + (paymentProviderLabel != null ? paymentProviderLabel : "") + "\n"
                + "Référence demande : #" + requestId + "\n\n"
                + "Connectez-vous pour valider ou refuser la demande :\n\n"
                + buildSuperAdminSubscriptionRequestsUrl() + "\n\n"
                + "Cordialement,\n"
                + "L'équipe Stock SaaS";
    }

    private String buildSubscriptionRejectedEmailContent(
            String recipientName,
            String companyName,
            String rejectionReason,
            String subscriptionsUrl
    ) {
        String reason = rejectionReason != null && !rejectionReason.isBlank()
                ? rejectionReason.trim()
                : "Demande refusée";

        return "Bonjour " + (recipientName != null && !recipientName.isBlank() ? recipientName : "Administrateur") + ",\n\n"
                + "Votre demande d'abonnement pour l'entreprise " + companyName + " n'a pas pu être validée.\n\n"
                + "Motif du refus :\n" + reason + "\n\n"
                + "Vous pouvez soumettre une nouvelle demande avec un justificatif de paiement corrigé "
                + "depuis la page Abonnement :\n\n"
                + subscriptionsUrl + "\n\n"
                + "Pour toute question, contactez le support Stock SaaS.\n\n"
                + "Cordialement,\n"
                + "L'équipe Stock SaaS";
    }
}
