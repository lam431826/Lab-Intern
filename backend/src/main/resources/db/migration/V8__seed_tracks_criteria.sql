-- Dữ liệu khởi tạo: 6 ngạch, tiêu chí chấm và tham số tính điểm mặc định.
-- Nguồn: "Tài liệu Hoạch định & Định hướng" (chỉ tiêu, số slot) và
--        "Câu hỏi Phỏng vấn & Đánh giá tuyển dụng" (tiêu chí, thang chấm).
-- SE và Game Developer lấy theo bản Word (bản Excel cũ đang lệch).
-- Graphic Design: tiêu chí 3–4 chưa có thang chấm trong tài liệu gốc, Lead bổ sung qua giao diện.
-- Mọi giá trị ở đây sửa được qua API, không cần migration mới.

-- ---------- Ngạch ----------
-- Kế hoạch gộp Game Dev và Game Design thành Ban 2 (10–12 slot, mục tiêu ~8–9);
-- vận hành thực tế tách 2 ca (8 + 3 slot), chỉ tiêu chia 7 + 2.
INSERT INTO tracks (code, name, quota, slot_min, slot_max, requires_portfolio, sort_order) VALUES
    ('AI',          N'AI',                         3, 4,  5, 0, 1),
    ('GRAPHIC',     N'Graphic Design',             5, 5,  7, 1, 2),
    ('SE',          N'Software Engineer',          8, 8, 10, 0, 3),
    ('GAME_DEV',    N'Game Developer',             7, 7,  9, 0, 4),
    ('IA',          N'IA (Information Assurance)', 3, 4,  6, 0, 5),
    ('GAME_DESIGN', N'Game Design',                2, 3,  4, 1, 6);

-- ---------- Tiêu chí chung (mọi ngạch) ----------
INSERT INTO criteria (track_id, crit_group, name, description, sort_order) VALUES
    (NULL, 'GENERAL', N'Thái độ & Tinh thần học hỏi',
     N'Câu hỏi gợi ý: Khi gặp một việc bạn chưa biết làm, bạn thường bắt đầu từ đâu? Gần đây bạn học được điều gì mới nhất? Bạn học nó như thế nào? Hướng chấm: lắng nghe tích cực, cầu tiến, thái độ tôn trọng; thể hiện mong muốn phát triển bản thân và đóng góp cho Lab.', 1),
    (NULL, 'GENERAL', N'Tư duy logic & Giải quyết vấn đề',
     N'Câu hỏi gợi ý: Bạn hãy mô tả lại một vấn đề bạn gặp phải và cách bạn xử lý nó? Hãy kể một task/bài tập bạn từng làm mà bạn nghĩ mình làm tốt và vì sao? Hướng chấm: cách tiếp cận bài toán mạch lạc, logic; đưa ra được phương án xử lý hợp lý dưới áp lực câu hỏi phản biện.', 2),
    (NULL, 'GENERAL', N'Tinh thần Teamwork',
     N'Câu hỏi gợi ý: Khi xảy ra bất đồng trong nhóm, bạn thường phản ứng thế nào? Trong nhóm, bạn thường là kiểu người như thế nào? Hướng chấm: sẵn sàng chia sẻ, hỗ trợ đồng đội, tôn trọng sự khác biệt ý kiến; có kinh nghiệm làm việc nhóm qua các dự án trước đây.', 3),
    (NULL, 'GENERAL', N'Tư duy phản biện & Xử lý tình huống',
     N'Câu hỏi gợi ý: Nếu còn 1 ngày đến deadline và bạn phát hiện lỗi, bạn sẽ làm gì? Hướng chấm: khả năng giữ bình tĩnh, bảo vệ quan điểm cá nhân có căn cứ; tiếp thu phản hồi trái chiều một cách văn minh, tích cực.', 4);

-- ---------- AI ----------
INSERT INTO criteria (track_id, crit_group, name, description, rubric_low, rubric_mid, rubric_high, sort_order)
SELECT t.id, 'PROFESSIONAL', v.name, v.descr, v.low, v.mid, v.high, v.ord
FROM tracks t CROSS JOIN (VALUES
    (1, N'Khả năng cập nhật công nghệ',
        N'Tiếp thu, tìm hiểu các công cụ, phương pháp mới nhất.',
        N'Thụ động, ngại thay đổi hoặc không có nhu cầu tiếp thu công nghệ/công cụ mới.',
        N'Nhận biết các xu hướng mới, đang chủ động tìm hiểu nhưng chưa thể ứng dụng.',
        N'Nhanh nhạy nắm bắt công nghệ mới, đánh giá và thử nghiệm sâu sắc, chủ động đề xuất áp dụng vào công việc để nâng cao hiệu suất.'),
    (2, N'Hiểu mô hình AI cơ bản (AI, ML, DL,...)',
        N'Nắm vững khái niệm, phân biệt rõ các nhóm mô hình, bản chất toán học/kiến trúc nền tảng.',
        N'Chưa nắm vững khái niệm, nhầm lẫn giữa AI/ML/DL.',
        N'Hiểu khái niệm cơ bản nhưng chưa sâu bản chất toán/kiến trúc.',
        N'Hiểu sâu bản chất, so sánh tốt ưu/nhược điểm từng mô hình để áp dụng đúng bài toán.'),
    (3, N'Quy trình làm việc AI (Data - Train - Evaluate - Deploy)',
        N'Hiểu và thực hành trọn vẹn quy trình phát triển sản phẩm AI từ thu thập/xử lý dữ liệu, huấn luyện, đánh giá mô hình đến triển khai thực tế.',
        N'Chưa nắm được đầy đủ quy trình hoặc thiếu kiến thức ở các bước quan trọng.',
        N'Nắm được khung quy trình chính nhưng chưa có kinh nghiệm triển khai.',
        N'Thành thạo quy trình End-to-End, biết cách tối ưu từng công đoạn (data quality, metrics evaluation, model deployment).'),
    (4, N'Sử dụng công cụ (AI Tools)',
        N'Khả năng nhận biết, lựa chọn và sử dụng thành thạo các công cụ AI (Copilot, ChatGPT, Claude, các thư viện/framework AI...) phù hợp với từng bài toán.',
        N'Không biết hoặc chưa biết cách ứng dụng các công cụ AI hỗ trợ công việc.',
        N'Biết đến các công cụ AI phổ biến, có thể thao tác cơ bản nhưng chưa thực sự linh hoạt hoặc tối ưu được công năng.',
        N'Sử dụng thành thạo và làm chủ nhiều công cụ AI; biết kết hợp linh hoạt các công cụ để giải quyết công việc tối ưu và nhanh chóng nhất.')
) AS v(ord, name, descr, low, mid, high)
WHERE t.code = 'AI';

-- ---------- Graphic Design ----------
INSERT INTO criteria (track_id, crit_group, name, description, rubric_low, rubric_mid, rubric_high, sort_order)
SELECT t.id, 'PROFESSIONAL', v.name, v.descr, v.low, v.mid, v.high, v.ord
FROM tracks t CROSS JOIN (VALUES
    (1, N'Thẩm mỹ & gu thiết kế',
        N'Có khả năng cảm nhận, đánh giá và ứng dụng. Có tinh thần ham học hỏi và sáng tạo.',
        N'Phân biệt giữa đẹp và xấu, phối màu theo bảng màu có sẵn, chủ yếu sao chép.',
        N'Có nhận thức về các loại thẩm mỹ khác nhau, biết ứng dụng bố cục, kết hợp học hỏi và tự sáng tạo.',
        N'Khả năng cảm nhận độc đáo, nhận thức cao và biết cách làm ra sản phẩm với nhiều loại thẩm mỹ khác nhau, nắm vững các loại bố cục, có tinh thần ham học hỏi.'),
    (2, N'UI/UX Thinking',
        N'Thấu hiểu hành vi người dùng và cách giải quyết. Biết dùng Figma.',
        N'Có thể sử dụng các nguyên lý thị giác đơn giản, biết áp dụng các bộ thư viện có sẵn (UI Kits, Material Design, ...). Thiết kế giao diện không để user flow bị đứt.',
        N'Có thể đảm bảo tính nhất quán của sản phẩm, có hiểu biết nhất định về tâm lí học hành vi, biết cân bằng tính thẩm mỹ và khả năng lập trình.',
        N'Sở hữu tư duy ở tầm chiến lược, có khả năng dự đoán hành vi và đưa ra giải pháp trước khi ra mắt sản phẩm, kết nối chặt trải nghiệm của người dùng với sản phẩm.'),
    (3, N'Công cụ Figma/PS/AI',
        N'Biết sử dụng các phần mềm Adobe, Figma, Canva (bắt buộc).',
        NULL, NULL, NULL),
    (4, N'Hiểu quy trình Dev handoff (khả năng đồng bộ với Dev)',
        NULL,
        NULL, NULL, NULL)
) AS v(ord, name, descr, low, mid, high)
WHERE t.code = 'GRAPHIC';

-- ---------- Software Engineer ----------
INSERT INTO criteria (track_id, crit_group, name, description, rubric_low, rubric_mid, rubric_high, sort_order)
SELECT t.id, 'PROFESSIONAL', v.name, v.descr, v.low, v.mid, v.high, v.ord
FROM tracks t CROSS JOIN (VALUES
    (1, N'Kiến thức lập trình cơ bản Java',
        N'Đọc, hiểu và vận dụng cú pháp cơ bản của Java Core.',
        N'Hiểu cú pháp Java cơ bản, đọc được đoạn mã đơn giản và giải thích được chức năng của chương trình.',
        N'Vận dụng được OOP và các kiến thức Java cơ bản để giải quyết bài toán.',
        N'Đọc hiểu mã nguồn phức tạp hơn, phát hiện được lỗi hoặc đề xuất cải thiện.'),
    (2, N'Tư duy Software Engineering',
        N'Khả năng tổ chức mã nguồn, phân tích vấn đề và xây dựng phần mềm theo hướng dễ bảo trì, mở rộng.',
        N'Hiểu được cấu trúc cơ bản của một dự án và vai trò của các thành phần.',
        N'Phân tích được luồng xử lý và đề xuất được cách triển khai phù hợp.',
        N'Nhận biết được các vấn đề trong thiết kế, cân nhắc khả năng bảo trì và ảnh hưởng của thay đổi tới hệ thống.'),
    (3, N'Tư duy giải quyết vấn đề',
        N'Khả năng phân tích nguyên nhân và lựa chọn giải pháp phù hợp khi gặp vấn đề trong quá trình phát triển phần mềm.',
        N'Xác định được vấn đề và đưa ra hướng xử lý cơ bản.',
        N'Phân tích được nguyên nhân, lựa chọn được giải pháp hợp lý và giải thích được lý do.',
        N'Biết chia nhỏ vấn đề, cân nhắc nhiều phương án, đánh giá ưu nhược điểm và lựa chọn giải pháp phù hợp với yêu cầu của bài toán.'),
    (4, N'Hiểu biết về dự án',
        N'Trình bày được dự án đã làm.',
        N'Trình bày được dự án và vai trò của bản thân.',
        N'Giải thích được phần việc bản thân đã thực hiện.',
        N'Hiểu được kiến trúc tổng thể và cách phần việc của bản thân vận hành trong dự án.'),
    (5, N'Khả năng học hỏi và thích nghi',
        N'Khả năng tiếp thu kiến thức mới, chủ động học hỏi và thích nghi với môi trường phát triển phần mềm.',
        N'Sẵn sàng tiếp thu góp ý và học hỏi từ người hướng dẫn.',
        N'Chủ động tìm hiểu tài liệu, công nghệ mới và có thể tự giải quyết các vấn đề cơ bản.',
        N'Chủ động nghiên cứu, nhanh chóng thích nghi với dự án mới và vận dụng được kiến thức đã học vào thực tế.')
) AS v(ord, name, descr, low, mid, high)
WHERE t.code = 'SE';

-- ---------- Game Developer ----------
INSERT INTO criteria (track_id, crit_group, name, description, rubric_low, rubric_mid, rubric_high, sort_order)
SELECT t.id, 'PROFESSIONAL', v.name, v.descr, v.low, v.mid, v.high, v.ord
FROM tracks t CROSS JOIN (VALUES
    (1, N'Kiến thức C# cơ bản',
        N'Hiểu các kiến thức cơ bản của C# để lập trình trong Unity (biến, điều kiện, vòng lặp, hàm, class).',
        N'Nắm được cú pháp cơ bản nhưng còn nhầm lẫn.',
        N'Sử dụng được C# để giải quyết các bài toán đơn giản.',
        N'Viết code rõ ràng, giải thích được cách hoạt động của chương trình.'),
    (2, N'Kiến thức Unity cơ bản',
        N'Biết sử dụng Unity Editor và các thành phần cơ bản như Scene, GameObject, Prefab, UI, Script.',
        N'Biết giao diện Unity nhưng thao tác còn chậm.',
        N'Có thể tự tạo một game đơn giản và sử dụng được các chức năng cơ bản.',
        N'Hiểu rõ cách hoạt động của các thành phần và biết áp dụng vào dự án.'),
    (3, N'Tư duy giải quyết vấn đề',
        N'Có khả năng phân tích nguyên nhân khi gặp lỗi và tìm cách khắc phục.',
        N'Thường phụ thuộc vào người khác khi gặp lỗi.',
        N'Biết đọc lỗi, tìm kiếm tài liệu và thử nhiều cách để giải quyết.',
        N'Phân tích được nguyên nhân gốc rễ và đề xuất giải pháp hợp lý.'),
    (4, N'Khả năng học hỏi',
        N'Chủ động tìm hiểu công nghệ mới và tiếp thu góp ý.',
        N'Chỉ học khi được giao nhiệm vụ.',
        N'Chủ động tìm tài liệu và cải thiện kỹ năng.',
        N'Có tinh thần tự học tốt, áp dụng kiến thức mới vào dự án.')
) AS v(ord, name, descr, low, mid, high)
WHERE t.code = 'GAME_DEV';

-- ---------- IA ----------
INSERT INTO criteria (track_id, crit_group, name, description, rubric_low, rubric_mid, rubric_high, sort_order)
SELECT t.id, 'PROFESSIONAL', v.name, v.descr, v.low, v.mid, v.high, v.ord
FROM tracks t CROSS JOIN (VALUES
    (1, N'Kiến thức nền tảng',
        N'Hiểu rõ mô hình CIA và AAA. Kiến thức nền tảng về Mạng.',
        N'Nhớ tên các khái niệm CIA, AAA, mô hình OSI/TCP nhưng chưa giải thích được chi tiết.',
        N'Nắm vững và giải thích mạch lạc các khái niệm CIA, AAA, mô hình OSI/TCP/IP.',
        N'Hiểu sâu sắc, có khả năng phân tích luồng gói tin, hiểu cách thức hoạt động của các thiết bị bảo mật mạng.'),
    (2, N'Kiến thức về Hệ điều hành',
        N'Linux/Unix: thành thạo các lệnh cơ bản trong terminal, hiểu cấu trúc thư mục, hệ thống phân quyền (permissions), quản lý tiến trình và logs. Windows: hiểu về Windows Registry, Active Directory cơ bản, Windows Event Logs và cơ chế quản lý User/Group.',
        N'Biết sử dụng một vài lệnh terminal/cmd cơ bản nhưng chưa hiểu sâu về cơ chế quản lý quyền.',
        N'Thao tác tốt trên terminal, hiểu rõ cơ chế phân quyền (chmod, chown), biết cách tìm kiếm và đọc logs cơ bản trên cả Linux và Windows.',
        N'Nắm vững kiến trúc OS, hiểu sâu về Active Directory, có khả năng phân tích Event Logs để tìm ra các dấu hiệu bất thường.'),
    (3, N'Kiến thức cơ bản về Bảo mật & Mật mã học',
        N'Lỗ hổng phổ biến: nắm được khái niệm cơ bản của OWASP Top 10. Mật mã học: phân biệt được mã hóa đối xứng (Symmetric), bất đối xứng (Asymmetric) và hàm băm (Hashing - MD5, SHA).',
        N'Nêu được tên một số lỗ hổng web.',
        N'Hiểu và giải thích được cơ chế khai thác của các lỗi phổ biến (SQLi, XSS, CSRF).',
        N'Không chỉ hiểu cơ chế khai thác mà còn đề xuất được phương án phòng chống (mitigation) an toàn.'),
    (4, N'Kỹ năng Lập trình và Scripting',
        N'Scripting: nắm vững ít nhất một ngôn ngữ như Python, Bash Script hoặc PowerShell. Đọc hiểu code: có khả năng đọc hiểu cơ bản C/C++, Java, PHP hoặc JavaScript để phân tích mã độc hoặc tìm lỗ hổng mã nguồn.',
        N'Biết đọc cú pháp code cơ bản nhưng chưa tự viết được script hoàn chỉnh để tự động hóa công việc.',
        N'Đọc hiểu được flow (luồng) của mã nguồn. Tự viết được các script Python/Bash đơn giản để hỗ trợ rà quét hoặc xử lý dữ liệu.',
        N'Đọc hiểu code tốt, có tư duy phát hiện lỗ hổng logic/bảo mật qua mã nguồn (White-box).'),
    (5, N'Sử dụng Công cụ (Security Tools)',
        N'Phân tích mạng: Wireshark, tcpdump. Rà quét & Trinh sát: Nmap, Dirb, Gobuster. Web Application: Burp Suite cơ bản (Intercept, Repeater, Intruder). Khác: Metasploit Framework (mức độ cơ bản).',
        N'Biết tên hoặc từng dùng qua một vài công cụ (Nmap, Wireshark) ở mức độ chạy lệnh/giao diện cơ bản theo hướng dẫn có sẵn.',
        N'Sử dụng thành thạo các tính năng quan trọng: bắt gói tin (Wireshark), rà quét port/service (Nmap), chặn và sửa request web (Burp Suite).',
        N'Kết hợp linh hoạt nhiều công cụ để phân tích chuyên sâu. Biết dùng các tính năng nâng cao.'),
    (6, N'Tham gia CTF, Chứng chỉ IA',
        N'Có kinh nghiệm tham gia các cuộc thi về An toàn thông tin và sở hữu các chứng chỉ liên quan đến ngành.',
        N'Có quan tâm nhưng chưa tham gia giải CTF nào, tài khoản lab (TryHackMe/HackTheBox) ở mức độ cơ bản. Chưa có chứng chỉ.',
        N'Từng tham gia các giải CTF (cấp trường/khu vực) hoặc có điểm số rèn luyện tốt trên các nền tảng Lab.',
        N'Có thành tích/giải thưởng cao tại các cuộc thi CTF uy tín. Đã sở hữu các chứng chỉ chuyên môn giá trị (như eJPT, CEH, PNPT...).')
) AS v(ord, name, descr, low, mid, high)
WHERE t.code = 'IA';

-- ---------- Game Design ----------
INSERT INTO criteria (track_id, crit_group, name, description, rubric_low, rubric_mid, rubric_high, sort_order)
SELECT t.id, 'PROFESSIONAL', v.name, v.descr, v.low, v.mid, v.high, v.ord
FROM tracks t CROSS JOIN (VALUES
    (1, N'Tư duy Phân tích Game',
        N'Khả năng phân tích các tựa game đã chơi qua các lăng kính thiết kế, bóc tách được lý do tại sao một cơ chế lại hoạt động tốt hoặc dở.',
        N'Chỉ kể lại nội dung game, không giải thích được lý do thiết kế đằng sau.',
        N'Phân tích được điểm mạnh/yếu cơ bản của một cơ chế và nêu được trải nghiệm mà nó mang lại cho người chơi.',
        N'Có góc nhìn hệ thống, phân tích sâu về tâm lý người chơi và mục đích thiết kế của nhà phát triển.'),
    (2, N'Thiết kế Coreloop Gameplay',
        N'Hiểu và xây dựng được một loop cơ bản (Hành động -> Phần thưởng -> Mở rộng) để tạo động lực cho người chơi.',
        N'Các ý tưởng cơ chế rời rạc, nhồi nhét, không tạo thành một vòng lặp liên kết.',
        N'Vẽ ra được một core loop rõ ràng, có tính logic nhân quả.',
        N'Vòng lặp chặt chẽ, cuốn hút, tạo được động lực chơi lại (replayability) rõ rệt.'),
    (3, N'Kỹ năng Trình bày & Viết tài liệu',
        N'Khả năng truyền đạt ý tưởng thiết kế một cách rành mạch, có cấu trúc để team Dev/Art dễ dàng đọc hiểu.',
        N'Diễn đạt lủng củng, viết thuần chữ dài dòng, khó nắm bắt ý chính.',
        N'Văn phong mạch lạc, biết dùng bullet point, chia đề mục rõ ràng.',
        N'Tài liệu súc tích, phân cấp thông tin cực tốt, Dev nhìn vào có thể hình dung ngay logic code cần làm.'),
    (4, N'Sử dụng Công cụ Trực quan hóa',
        N'Khả năng trực quan hóa các luồng logic (user flow) hoặc vẽ phác thảo giao diện (UI) cơ bản thay vì chỉ nói miệng.',
        N'Trình bày ý tưởng hoàn toàn bằng miệng hoặc chữ, không biết vẽ sơ đồ.',
        N'Biết dùng các tool cơ bản (Draw.io, Miro, hoặc vẽ tay) để làm sơ đồ luồng mạch lạc.',
        N'Sử dụng thành thạo Figma/Balsamiq để làm mockup chi tiết hoặc tạo được prototype tương tác cơ bản.')
) AS v(ord, name, descr, low, mid, high)
WHERE t.code = 'GAME_DESIGN';

-- ---------- Tham số tính điểm ----------
-- Tổng = 0,4 × Điểm chung + 0,6 × Điểm chuyên môn; Đạt sơ bộ khi Tổng ≥ 7,0 (mức Khá – Đạt).
-- SLA duyệt CV 36 giờ; slot phỏng vấn 15 phút.
INSERT INTO scoring_settings (id, weight_general, weight_professional, pass_threshold, sla_hours, slot_minutes)
VALUES (1, 0.40, 0.60, 7.00, 36, 15);
