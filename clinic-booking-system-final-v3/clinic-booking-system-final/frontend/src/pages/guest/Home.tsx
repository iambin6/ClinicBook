import Banner from '../../components/home/Banner';
import FeaturesSection from '../../components/home/FeaturesSection';
import DifferenceSection from '../../components/home/DifferenceSection';
import ServicesSection from '../../components/home/ServicesSection';
import DoctorsSection from '../../components/home/DoctorsSection';
import UpcomingEvents from '../../components/home/UpcomingEvents';
import NewsSection from '../../components/home/NewsSection';
import Chatbot from '../../components/chatbot/Chatbot';

export default function Home(){
  return (
    <>
      <Banner />
      <FeaturesSection />
      <DifferenceSection />
      <ServicesSection />
      <DoctorsSection />
      <UpcomingEvents />
      <NewsSection />
      <Chatbot />
    </>
  );
}